import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.URL;
import java.util.ResourceBundle;

import javafx.application.Application;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class App extends Application implements Runnable {

    private MulticastSocket multicast;
    private Socket filetransfer;

    @FXML
    private ResourceBundle resources;

    @FXML
    private URL location;

    @FXML
    private AnchorPane anchorpane;

    @FXML
    private TextField groupIP;

    @FXML
    private TextField groupPort;

    @FXML
    private TextField peerID;

    @FXML
    private TextField receptionPort;

    @FXML
    private Button set;

    @FXML
    private TextArea output;

    @FXML
    private TextField filename;

    @FXML
    private Button search;

    public static void main(String[] args) {
        Application.launch(args);
    }

    @FXML
    void connect(ActionEvent event) {
        try {
            multicast = new MulticastSocket(Integer.parseInt(groupPort.getText().trim()));
            multicast.joinGroup(InetAddress.getByName(groupIP.getText().trim()));
            System.out.println(multicast.getInetAddress()+" "+multicast.getPort());
            new Thread(this).start();
        } catch (NumberFormatException | IOException e) {
            System.err.println(e.getLocalizedMessage());
        }
    }

    @FXML
    void searchFile(ActionEvent event) {
        DatagramPacket datagramPacket = new DatagramPacket(filename.getText().trim().getBytes(),filename.getText().trim().getBytes().length);
        try {
            datagramPacket.setAddress(InetAddress.getByName(groupIP.getText().trim()));
            datagramPacket.setPort(Integer.parseInt(groupPort.getText().trim()));
            DatagramSocket socket = new DatagramSocket();
            System.out.println("1");
            socket.send(datagramPacket);
            socket.close();
            long timer = System.currentTimeMillis()+1000;
            do{
                multicast.receive(datagramPacket);
            }while(datagramPacket.getAddress().getHostAddress().equals(InetAddress.getLocalHost().getHostAddress()) && timer-System.currentTimeMillis()>0);
            System.out.println("4");
            output.appendText("\n"+new String(datagramPacket.getData(),0,datagramPacket.getData().length));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        System.out.println("starting helper thread");
        
        DatagramPacket datagramPacket= new DatagramPacket("".getBytes(), 0);
        while (true) {
            try {
                long timer = System.currentTimeMillis()+1000;
                do{
                    multicast.receive(datagramPacket);
                }while(datagramPacket.getAddress().getHostAddress().equals(InetAddress.getLocalHost().getHostAddress()) && timer-System.currentTimeMillis()>0); 
                System.out.println("2");
                if(datagramPacket!=null){
                    String filename = new String(datagramPacket.getData(),0,datagramPacket.getData().length);
                    File file = new File("shared", filename);
                    if(file.exists()){
                        String result = String.format("Files %s is present with %s",filename,peerID.getText());
                        datagramPacket = new DatagramPacket(result.getBytes(), result.getBytes().length);
                        datagramPacket.setAddress(InetAddress.getByName(groupIP.getText().trim()));
                        datagramPacket.setPort(Integer.parseInt(groupPort.getText().trim()));
                        System.out.println("3");
                        multicast.send(datagramPacket);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void start(Stage stage) throws IOException
    {
        FXMLLoader loader = new FXMLLoader();
        FileInputStream fileInputStream = new FileInputStream("src\\gui.fxml");
        stage.setScene(new Scene((VBox) loader.load(fileInputStream)));
        stage.setTitle("My DistributedFS");
        stage.show();
    }
}
