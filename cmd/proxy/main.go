package main

import (
	"bytes"
	"errors"
	"fmt"
	"io"
	"log"
	"net"
	"sync"
)

const (
	ListenAddr  = "0.0.0.0:25566"
	BackendAddr = "127.0.0.1:25565"
)

const (
	Handshaking = 0
	Status      = 1
	Login       = 2
	Play        = 3
)

func main() {
	listener, err := net.Listen("tcp", ListenAddr)
	if err != nil {
		log.Fatalf("Fail: %v", err)
	}
	defer listener.Close()

	log.Printf("Proxy 775 active on %s", ListenAddr)
	go startUDPProxy()

	for {
		conn, err := listener.Accept()
		if err != nil {
			continue
		}
		go handleConnection(conn)
	}
}

func handleConnection(clientConn net.Conn) {
	defer clientConn.Close()

	backendConn, err := net.Dial("tcp", BackendAddr)
	if err != nil {
		return
	}
	defer backendConn.Close()

	var state int = Handshaking
	var wg sync.WaitGroup
	wg.Add(2)

	go func() {
		defer wg.Done()
		defer backendConn.Close()
		for {
			data, id, err := readPacket(clientConn)
			if err != nil {
				return
			}

			if state == Handshaking && id == 0x00 {
				state, _ = handleHandshake(data)
			} else if state == Play {
				if id == 0x04 || id == 0x03 {
					if interceptCommand(data, clientConn) {
						continue
					}
				}
			}
			writeRawPacket(backendConn, id, data)
		}
	}()

	go func() {
		defer wg.Done()
		defer clientConn.Close()
		for {
			data, id, err := readPacket(backendConn)
			if err != nil {
				return
			}
			if state == Play && (id == 0x11 || id == 0x12 || id == 0x13) {
				data = injectCommand(data)
			}
			writeRawPacket(clientConn, id, data)
		}
	}()

	wg.Wait()
}

func handleHandshake(data []byte) (int, int) {
	buf := bytes.NewBuffer(data)
	version, _ := readVarInt(buf)
	readString(buf)
	binaryReadUint16(buf)
	nextState, _ := readVarInt(buf)
	return nextState, version
}

func injectCommand(data []byte) []byte {
	buf := bytes.NewBuffer(data)
	count, err := readVarInt(buf)
	if err != nil || count <= 0 {
		return data
	}

	res := new(bytes.Buffer)
	writeVarInt(res, count+1)
	
	nodesData := buf.Bytes()
	if len(nodesData) < 1 {
		return data
	}
	
	rootIndex := nodesData[len(nodesData)-1]
	res.Write(nodesData[:len(nodesData)-1])

	res.WriteByte(0x01)
	writeVarInt(res, 0)
	writeString(res, "server")
	
	res.WriteByte(rootIndex)
	
	return res.Bytes()
}

func interceptCommand(data []byte, client net.Conn) bool {
	buf := bytes.NewBuffer(data)
	msg := readString(buf)

	if msg == "server" || msg == "/server" || msg == "server " || msg == "/server " {
		sendSystemMessage(client, "§a[Proxy] §fServeur Principal §7(Protocol 775)")
		return true
	}
	return false
}

func sendSystemMessage(client net.Conn, text string) {
	msgJson := fmt.Sprintf(`{"text":"%s"}`, text)
	payload := new(bytes.Buffer)
	writeString(payload, msgJson)
	payload.WriteByte(0)
	payload.Write(make([]byte, 16))
	writeRawPacket(client, 0x0F, payload.Bytes())
}

func readPacket(r io.Reader) ([]byte, int, error) {
	length, err := readVarInt(r)
	if err != nil {
		return nil, 0, err
	}
	packetData := make([]byte, length)
	if _, err := io.ReadFull(r, packetData); err != nil {
		return nil, 0, err
	}
	buf := bytes.NewBuffer(packetData)
	id, _ := readVarInt(buf)
	return buf.Bytes(), id, nil
}

func writeRawPacket(w io.Writer, id int, data []byte) {
	idBuf := new(bytes.Buffer)
	writeVarInt(idBuf, id)
	writeVarInt(w, idBuf.Len()+len(data))
	w.Write(idBuf.Bytes())
	w.Write(data)
}

func readVarInt(r io.Reader) (int, error) {
	var v int
	for i := 0; i < 5; i++ {
		var b [1]byte
		if _, err := r.Read(b[:]); err != nil {
			return 0, err
		}
		v |= int(b[0]&0x7F) << uint(7*i)
		if b[0]&0x80 == 0 {
			return v, nil
		}
	}
	return 0, errors.New("err")
}

func writeVarInt(w io.Writer, v int) {
	for v >= 0x80 {
		w.Write([]byte{byte(v | 0x80)})
		v >>= 7
	}
	w.Write([]byte{byte(v)})
}

func readString(r io.Reader) string {
	l, _ := readVarInt(r)
	s := make([]byte, l)
	io.ReadFull(r, s)
	return string(s)
}

func writeString(w io.Writer, s string) {
	writeVarInt(w, len(s))
	w.Write([]byte(s))
}

func binaryReadUint16(r io.Reader) uint16 {
	var b [2]byte
	r.Read(b[:])
	return uint16(b[0])<<8 | uint16(b[1])
}

func startUDPProxy() {
	addr, _ := net.ResolveUDPAddr("udp", ListenAddr)
	conn, _ := net.ListenUDP("udp", addr)
	bAddr, _ := net.ResolveUDPAddr("udp", BackendAddr)
	clients := make(map[string]*net.UDPConn)
	var mu sync.Mutex
	buf := make([]byte, 2048)
	for {
		n, cAddr, _ := conn.ReadFromUDP(buf)
		s := cAddr.String()
		mu.Lock()
		cb, ok := clients[s]
		if !ok {
			cb, _ = net.DialUDP("udp", nil, bAddr)
			clients[s] = cb
			go func(c *net.UDPConn, a *net.UDPAddr) {
				b := make([]byte, 2048)
				for {
					bn, _, err := c.ReadFromUDP(b)
					if err != nil { break }
					conn.WriteToUDP(b[:bn], a)
				}
			}(cb, cAddr)
		}
		mu.Unlock()
		cb.Write(buf[:n])
	}
}
