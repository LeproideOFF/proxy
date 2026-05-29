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

// Minecraft States
const (
	Handshaking = 0
	Status      = 1
	Login       = 2
	Play        = 3
)

func main() {
	listener, err := net.Listen("tcp", ListenAddr)
	if err != nil {
		log.Fatalf("Failed to listen: %v", err)
	}
	defer listener.Close()

	log.Printf("[Proxy] Java Proxy listening on %s", ListenAddr)
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
		log.Printf("Backend offline: %v", err)
		return
	}
	defer backendConn.Close()

	// Interceptor logic
	state := Handshaking
	
	// Bridge with interception
	var wg sync.WaitGroup
	wg.Add(2)

	// Client -> Backend (Where we catch commands)
	go func() {
		defer wg.Done()
		defer backendConn.Close()
		
		for {
			packet, id, err := readPacket(clientConn)
			if err != nil {
				return
			}

			// Intercept Handshake to track state
			if state == Handshaking && id == 0x00 {
				state = handleHandshake(packet)
			} else if state == Play && id == 0x03 { // Chat Packet (legacy) or Command (modern)
				if interceptCommand(packet, clientConn, backendConn) {
					continue // Don't forward if we handled it
				}
			}

			// Forward packet
			writeRawPacket(backendConn, id, packet)
		}
	}()

	// Backend -> Client
	go func() {
		defer wg.Done()
		defer clientConn.Close()
		io.Copy(clientConn, backendConn)
	}()

	wg.Wait()
}

func handleHandshake(data []byte) int {
	buf := bytes.NewBuffer(data)
	readVarInt(buf) // Protocol Version
	readString(buf) // Server Address
	binaryReadUint16(buf) // Port
	nextState, _ := readVarInt(buf)
	return nextState
}

func interceptCommand(data []byte, client net.Conn, backend net.Conn) bool {
	buf := bytes.NewBuffer(data)
	msg := readString(buf)

	if msg == "/server" {
		sendSystemMessage(client, "§a[Proxy] Vous êtes sur le serveur principal.")
		return true
	}
	// Add more commands here
	return false
}

func sendSystemMessage(client net.Conn, text string) {
	// Simple Chat Message Packet construction (Modern JSON format)
	// This is simplified and depends on protocol version
	msgJson := fmt.Sprintf(`{"text":"%s"}`, text)
	payload := new(bytes.Buffer)
	writeString(payload, msgJson)
	payload.WriteByte(0) // Position: Chat
	payload.Write(make([]byte, 16)) // Sender UUID (empty)
	
	writeRawPacket(client, 0x0F, payload.Bytes()) // 0x0F = Chat Message (Clientbound)
}

// --- Protocol Helpers ---

func readPacket(r io.Reader) (data []byte, id int, err error) {
	length, err := readVarInt(r)
	if err != nil {
		return nil, 0, err
	}

	packetData := make([]byte, length)
	_, err = io.ReadFull(r, packetData)
	if err != nil {
		return nil, 0, err
	}

	buf := bytes.NewBuffer(packetData)
	id, err = readVarInt(buf)
	return buf.Bytes(), id, err
}

func writeRawPacket(w io.Writer, id int, data []byte) {
	idBuf := new(bytes.Buffer)
	writeVarInt(idBuf, id)
	
	packetLen := idBuf.Len() + len(data)
	writeVarInt(w, packetLen)
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
	return 0, errors.New("VarInt too big")
}

func writeVarInt(w io.Writer, v int) {
	for v >= 0x80 {
		w.Write([]byte{byte(v | 0x80)})
		v >>= 7
	}
	w.Write([]byte{byte(v)})
}

func readString(r io.Reader) string {
	len, _ := readVarInt(r)
	str := make([]byte, len)
	io.ReadFull(r, str)
	return string(str)
}

func writeString(w io.Writer, s string) {
	writeVarInt(w, len(s))
	w.Write([]byte(s))
}

func binaryReadUint16(r io.Reader) uint16 {
	var v uint16
	var b [2]byte
	r.Read(b[:])
	return uint16(b[0])<<8 | uint16(b[1])
}

// UDP Proxy kept same for Geyser
func startUDPProxy() {
	addr, _ := net.ResolveUDPAddr("udp", ListenAddr)
	conn, err := net.ListenUDP("udp", addr)
	if err != nil {
		return
	}
	backendAddr, _ := net.ResolveUDPAddr("udp", BackendAddr)
	clients := make(map[string]*net.UDPConn)
	var mu sync.Mutex
	buf := make([]byte, 2048)
	for {
		n, clientAddr, _ := conn.ReadFromUDP(buf)
		addrStr := clientAddr.String()
		mu.Lock()
		cb, ok := clients[addrStr]
		if !ok {
			cb, _ = net.DialUDP("udp", nil, backendAddr)
			clients[addrStr] = cb
			go func(c *net.UDPConn, a *net.UDPAddr) {
				b := make([]byte, 2048)
				for {
					bn, _, _ := c.ReadFromUDP(b)
					conn.WriteToUDP(b[:bn], a)
				}
			}(cb, clientAddr)
		}
		mu.Unlock()
		cb.Write(buf[:n])
	}
}
