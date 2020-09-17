import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.ServerSocket;
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

/**
 * This main class acts as multicast listener, GUI Controller and distributed Filesystem handler.
 */
public class App extends Application implements Runnable {

    /**
     * Multicast listener used to receive messages on multicast network
     */
    private MulticastSocket multicast;
    /**
     * Socket used to send datagrams to multicast network.
     */
    private DatagramSocket datagramSocket;
    /**
     * Number of hosts connected to the current multicast network
     */
    private int hostsConnected;
    /**
     * filetransfer socket used for sending file from current host.
     */
    private ServerSocket filetransfer;
    /**
     * The listener thread which receives and servers the multicast datagrams.
     */
    private Thread listener;

    @FXML
    private ResourceBundle resources;

    @FXML
    private URL location;

    @FXML
    private AnchorPane anchorpane;

    /**
     * This field holds the multicast IP address
     */
    @FXML
    private TextField groupIP;

    /**
     * This field holds the multicast port number
     */
    @FXML
    private TextField groupPort;

    /**
     * This field holds the current peer's unique ID
     */
    @FXML
    private TextField peerID;

    /**
     * This field hold the filetransfer serversocket's listening port
     */
    @FXML
    private TextField receptionPort;

    /**
     * This button connects the peer to multicast filesystem
     */
    @FXML
    private Button set;

    /**
     * The outputs are displayed in this textarea
     */
    @FXML
    private TextArea output;

    /**
     * This field contains the filename to search and download
     */
    @FXML
    private TextField filename;

    /**
     * This button is used to initiate the search operation
     */
    @FXML
    private Button search;

    /**
     * This field holds the shared folder's relative address
     */
    @FXML
    private TextField folder;

    public static void main(String[] args) {
        Application.launch(args);
    }

    /**
     * This method is called when Set button is clicked
     * 
     * This method initializes filetransfer, joins multicast network using MulticastSocket and informs the other peers about its arrival.
     */
    @FXML
    void connect(ActionEvent event) {
        try {
            filetransfer = new ServerSocket(Integer.parseInt(receptionPort.getText().trim()));
            System.out.println(filetransfer.toString());
            multicast = new MulticastSocket(Integer.parseInt(groupPort.getText().trim()));
            multicast.joinGroup(InetAddress.getByName(groupIP.getText().trim()));
            datagramSocket = new DatagramSocket();
            hostsConnected=1;
            listener = new Thread(this);
            listener.start();
            String message = String.format("hosts:%s:%d",peerID.getText().trim(),hostsConnected);
            DatagramPacket datagramPacket = new DatagramPacket(message.getBytes(),message.getBytes().length,InetAddress.getByName(groupIP.getText().trim()),Integer.parseInt(groupPort.getText().trim()));
            datagramSocket = new DatagramSocket();
            datagramSocket.send(datagramPacket);
            datagramSocket.close();
        } catch (NumberFormatException | IOException e) {
            System.out.println(e.getMessage());
        }
    }

    /**
     * This method sends datagrams to multicast overlay about file to search. 
     * The message is of the format "search:[peerId]:[filename]"
     */
    @FXML
    void searchFile(ActionEvent event) {
        try {    
            String message = String.format("search:%s:%s",peerID.getText().trim(),filename.getText().trim());
            DatagramPacket datagramPacket = new DatagramPacket(message.getBytes(),message.getBytes().length,InetAddress.getByName(groupIP.getText().trim()),Integer.parseInt(groupPort.getText().trim()));
            datagramSocket = new DatagramSocket();
            datagramSocket.send(datagramPacket);
            datagramSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * This method acts as listener and performs following function:
     * The response to the datagram received depends on the message.
     * The message is of the form [function]:[sender]:....
     * The following three functions are implemented
     * 1. hosts :  display's message when new host is connected.
     * 2. search : sends appropriate response when file requested by other peer is present with current peer.
     * 3. found : initiates TCP connection to receive file when file is found.
     */
    @Override
    public void run() {
        System.out.println("starting listener thread");
        while (true) {
            try {
                DatagramPacket datagramPacket= new DatagramPacket(new byte[2048],2048);
                multicast.receive(datagramPacket); 
                String message = new String(datagramPacket.getData(),datagramPacket.getOffset(),datagramPacket.getLength());
                String[] fields = message.split(":");
                if(fields.length<3) continue;
                if(fields[1].equals(peerID.getText().trim())) continue;
                switch(fields[0]){
                    case "hosts": 
                        if(fields[2].equals("1")){
                            this.hostsConnected++;
                            output.appendText(String.format("\nNew Host :%s",fields[1]));
                            message = String.format("hosts:%s:%d",peerID.getText().trim(),hostsConnected);
                            datagramPacket = new DatagramPacket(message.getBytes(),message.getBytes().length,InetAddress.getByName(groupIP.getText().trim()),Integer.parseInt(groupPort.getText().trim()));
                        }else{
                            this.hostsConnected=Integer.parseInt(fields[2]);
                        }
                        break;
                    case "search": 
                        String filename = fields[2];
                        File file = new File(folder.getText().trim(), filename);
                        if(file.exists()){
                            message = String.format("found:%s:%s:%s:%s:%s",peerID.getText().trim(),fields[1],filename,filetransfer.getInetAddress().getHostName(),receptionPort.getText().trim());
                            datagramPacket = new DatagramPacket(message.getBytes(),message.getBytes().length,InetAddress.getByName(groupIP.getText().trim()),Integer.parseInt(groupPort.getText().trim()));
                            datagramSocket = new DatagramSocket();
                            datagramSocket.send(datagramPacket);
                            datagramSocket.close();
                            sendFile(file);
                        }
                        break;
                    case "found":
                        File file1 = new File(folder.getText().trim(), fields[3]);
                        if(fields[2].equals(peerID.getText().trim())){
                            output.appendText(String.format("\nFile Found :%s with %s",fields[3],fields[1]));
                            Socket s = new Socket(InetAddress.getByName(fields[4]),Integer.parseInt(fields[5]));
                            receiveFile(s, file1);
                            output.appendText(String.format("\nFile Saved :%s from %s",fields[3],fields[1]));
                        }
                        break;
                }                
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * This method handles the TCP server's function when transferring a file
     */
    public void sendFile(File file){
        try {
            Socket s = filetransfer.accept();
            BufferedInputStream bufferedInputStream = new BufferedInputStream(new FileInputStream(file));
            BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(s.getOutputStream());
            byte[] data = new byte[1024];
            while(bufferedInputStream.read(data)>0) bufferedOutputStream.write(data);
            bufferedInputStream.close();
            bufferedOutputStream.close();
            s.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * This method handles the TCP client's function when receiving a file
     */
    public void receiveFile(Socket s,File file){
        try {
            BufferedInputStream bufferedInputStream = new BufferedInputStream(s.getInputStream());
            BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(file));
            byte[] data = new byte[1024];
            while(bufferedInputStream.read(data)>0) bufferedOutputStream.write(data);
            bufferedInputStream.close();
            bufferedOutputStream.close();
            s.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void stop() throws Exception {
        listener.stop();
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
