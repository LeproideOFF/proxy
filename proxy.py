import asyncio
import socket

# Configuration
LISTEN_PORT = 25566
BACKEND_HOST = '127.0.0.1'
BACKEND_PORT = 25565

async def handle_tcp_client(reader, writer):
    """Handles Java Edition TCP traffic."""
    addr = writer.get_extra_info('peername')
    print(f"[*] Java connection from {addr}")
    
    try:
        # Connect to the backend server
        backend_reader, backend_writer = await asyncio.open_connection(BACKEND_HOST, BACKEND_PORT)
        
        # Forward data between client and backend
        async def forward(src_reader, dst_writer):
            try:
                while True:
                    data = await src_reader.read(4096)
                    if not data:
                        break
                    dst_writer.write(data)
                    await dst_writer.drain()
            except Exception:
                pass
            finally:
                dst_writer.close()

        await asyncio.gather(
            forward(reader, backend_writer),
            forward(backend_reader, writer)
        )
    except Exception as e:
        print(f"[!] TCP Error: {e}")
    finally:
        writer.close()
        print(f"[*] Java connection closed: {addr}")

class UDPProxyProtocol(asyncio.DatagramProtocol):
    """Handles Bedrock Edition (Geyser) UDP traffic."""
    def __init__(self):
        self.transport = None
        self.backend_address = (BACKEND_HOST, BACKEND_PORT)
        self.clients = {} # (addr) -> backend_socket

    def connection_made(self, transport):
        self.transport = transport
        print(f"[*] UDP Proxy (Bedrock) listening on {LISTEN_PORT}...")

    def datagram_received(self, data, addr):
        # If we haven't seen this client, create a backend socket for them
        if addr not in self.clients:
            print(f"[*] Bedrock connection from {addr}")
            loop = asyncio.get_event_loop()
            
            # Create a separate UDP socket to talk to the backend
            backend_sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            backend_sock.setblocking(False)
            
            # Helper to receive from backend and send back to client
            def receive_from_backend():
                try:
                    while True:
                        resp, _ = backend_sock.recvfrom(4096)
                        if not resp:
                            break
                        self.transport.sendto(resp, addr)
                except BlockingIOError:
                    pass
                except Exception as e:
                    print(f"[!] UDP Backend Error: {e}")

            loop.add_reader(backend_sock.fileno(), receive_from_backend)
            self.clients[addr] = backend_sock

        # Forward client data to backend
        self.clients[addr].sendto(data, self.backend_address)

async def main():
    # Start TCP Server
    tcp_server = await asyncio.start_server(handle_tcp_client, '0.0.0.0', LISTEN_PORT)
    print(f"[*] Java Proxy listening on {LISTEN_PORT}...")

    # Start UDP Server
    loop = asyncio.get_event_loop()
    await loop.create_datagram_endpoint(
        lambda: UDPProxyProtocol(),
        local_addr=('0.0.0.0', LISTEN_PORT)
    )

    async with tcp_server:
        await tcp_server.serve_forever()

if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        pass
