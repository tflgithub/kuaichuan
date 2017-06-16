import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;

/**
 * Created by Administrator on 2017/6/9.
 */

public class UdpServer {

    public static void main(String[] args) {

        byte[] bytes = new byte[1024];
        DatagramSocket datagramSocket = null;
        DatagramPacket datagramPacket = new DatagramPacket(bytes, bytes.length);

        try {
            datagramSocket = new DatagramSocket(8888);
            while (true) {
                try {
                    datagramSocket.receive(datagramPacket);
                    System.out.println(new String(bytes, 0, datagramPacket.getLength()));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }
    }
}
