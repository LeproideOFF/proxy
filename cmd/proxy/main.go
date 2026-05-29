package main

import (
	"bytes"
	"compress/zlib"
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

func main() {
	l, err := net.Listen("tcp", ListenAddr)
	if err != nil {
		log.Fatalf("Fail: %v", err)
	}
	defer l.Close()
	log.Printf("Proxy active on %s", ListenAddr)
	for {
		c, err := l.Accept()
		if err != nil {
			continue
		}
		go handle(c)
	}
}

type session struct {
	client      net.Conn
	backend     net.Conn
	compThreshold int
	state       int
}

func handle(c net.Conn) {
	defer c.Close()
	b, err := net.Dial("tcp", BackendAddr)
	if err != nil {
		return
	}
	defer b.Close()
	s := &session{client: c, backend: b, compThreshold: -1, state: 0}
	var wg sync.WaitGroup
	wg.Add(2)
	go func() {
		defer wg.Done()
		for {
			id, data, err := s.readPacket(s.client)
			if err != nil {
				return
			}
			if s.state == 0 && id == 0x00 {
				s.handleHandshake(data)
			} else if s.state >= 3 {
				if id == 0x04 || id == 0x03 {
					if s.handleCommand(data) {
						continue
					}
				}
			}
			s.writePacket(s.backend, id, data)
		}
	}()
	go func() {
		defer wg.Done()
		for {
			id, data, err := s.readPacket(s.backend)
			if err != nil {
				return
			}
			if s.state == 2 {
				if id == 0x03 {
					s.handleSetCompression(data)
				} else if id == 0x02 {
					s.state = 3
				}
			} else if s.state == 3 {
				if id == 0x02 {
					s.state = 4
				}
			}
			s.writePacket(s.client, id, data)
		}
	}()
	wg.Wait()
}

func (s *session) readPacket(r io.Reader) (int, []byte, error) {
	length, err := readVarInt(r)
	if err != nil {
		return 0, nil, err
	}
	data := make([]byte, length)
	if _, err := io.ReadFull(r, data); err != nil {
		return 0, nil, err
	}
	if s.compThreshold >= 0 {
		buf := bytes.NewBuffer(data)
		uncompressedLen, _ := readVarInt(buf)
		if uncompressedLen > 0 {
			zr, err := zlib.NewReader(buf)
			if err != nil {
				return 0, nil, err
			}
			data = make([]byte, uncompressedLen)
			io.ReadFull(zr, data)
			zr.Close()
		} else {
			data = buf.Bytes()
		}
	}
	buf := bytes.NewBuffer(data)
	id, _ := readVarInt(buf)
	return id, buf.Bytes(), nil
}

func (s *session) writePacket(w io.Writer, id int, data []byte) {
	pk := new(bytes.Buffer)
	writeVarInt(pk, id)
	pk.Write(data)
	raw := pk.Bytes()
	if s.compThreshold >= 0 {
		var b bytes.Buffer
		if len(raw) >= s.compThreshold {
			writeVarInt(&b, len(raw))
			zw := zlib.NewWriter(&b)
			zw.Write(raw)
			zw.Close()
		} else {
			writeVarInt(&b, 0)
			b.Write(raw)
		}
		raw = b.Bytes()
	}
	writeVarInt(w, len(raw))
	w.Write(raw)
}

func (s *session) handleHandshake(data []byte) {
	buf := bytes.NewBuffer(data)
	readVarInt(buf)
	readString(buf)
	var b [2]byte
	io.ReadFull(buf, b[:])
	st, _ := readVarInt(buf)
	s.state = st
}

func (s *session) handleSetCompression(data []byte) {
	buf := bytes.NewBuffer(data)
	v, _ := readVarInt(buf)
	s.compThreshold = v
}

func (s *session) handleCommand(data []byte) bool {
	buf := bytes.NewBuffer(data)
	cmd := readString(buf)
	if cmd == "server" || cmd == "/server" {
		s.sendMessage("§a[Proxy] §fOK")
		return true
	}
	return false
}

func (s *session) sendMessage(txt string) {
	msg := fmt.Sprintf(`{"text":"%s"}`, txt)
	pk := new(bytes.Buffer)
	writeString(pk, msg)
	pk.WriteByte(0)
	pk.Write(make([]byte, 16))
	s.writePacket(s.client, 0x67, pk.Bytes())
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
	return 0, fmt.Errorf("err")
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
	if l < 0 || l > 32767 {
		return ""
	}
	s := make([]byte, l)
	io.ReadFull(r, s)
	return string(s)
}

func writeString(w io.Writer, s string) {
	writeVarInt(w, len(s))
	w.Write([]byte(s))
}
