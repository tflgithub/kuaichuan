import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;

/**
 * Created by Administrator on 2017/6/9.
 */

public class UdpClient {

    public static void main(String[] args) {
        byte[] bytes = "Hello udp".getBytes();
        DatagramSocket datagramSocket;
        DatagramPacket senddp;
        try {
            senddp = new DatagramPacket(bytes, bytes.length, new InetSocketAddress("127.0.0.1", 8888));
            datagramSocket = new DatagramSocket();
            datagramSocket.send(senddp);
        } catch (SocketException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
