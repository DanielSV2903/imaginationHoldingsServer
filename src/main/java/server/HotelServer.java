package server;


import com.imaginationHoldings.data.GuestData;
import com.imaginationHoldings.data.HotelData;
import com.imaginationHoldings.data.HotelServiceData;
import com.imaginationHoldings.data.RoomData;
import com.imaginationHoldings.domain.Guest;
import com.imaginationHoldings.domain.Hotel;
import com.imaginationHoldings.domain.Room;
import com.imaginationHoldings.domain.RoomType;
import com.imaginationHoldings.protocol.Protocol;

import java.io.*;
import java.net.*;
import java.util.*;

public class HotelServer {
    private static HotelData hotelData;
    private static RoomData roomData;
    private static GuestData guestData;
    private static HotelServiceData hotelServiceData;
    private static List<Hotel> hotels;


    public static void main(String[] args) throws IOException {
        hotels=new ArrayList<>();
        hotelData=new HotelData("C:\\Users\\DanielSV\\Documents\\2025\\proyecto progra2\\imaginationHoldingsServer\\data\\hotels.dat");
        roomData=new RoomData("C:\\Users\\DanielSV\\Documents\\2025\\proyecto progra2\\imaginationHoldingsServer\\data\\rooms.dat");
        guestData=new GuestData("C:\\Users\\DanielSV\\Documents\\2025\\proyecto progra2\\imaginationHoldingsServer\\data\\guests.dat");
        hotelServiceData=new HotelServiceData(hotelData,roomData);
        preloadHotels();
        ServerSocket serverSocket = new ServerSocket(5000);
        System.out.println("Servidor escuchando en puerto 5000...");

        while (true) {
            Socket clientSocket = serverSocket.accept();
            new Thread(new ClientHandler(clientSocket)).start();
        }
    }

    private static void preloadHotels() throws IOException {
        hotels=hotelData.findAll();
    }

    private static class ClientHandler implements Runnable {
        private Socket socket;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (
                    BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)
            ) {
                String request;
                while ((request = reader.readLine()) != null) {
                    String[] parts = request.split("\\|");
                    String command = parts[0];

                    switch (command) {
                        case Protocol.GET_ALL_HOTELS -> {
                            hotels = hotelData.findAll();
                            StringBuilder sb = new StringBuilder();
                            for (Hotel h : hotels) sb.append(h).append("\n");
                            writer.println(sb.toString());
                        }

                        case Protocol.REGISTER_HOTEL -> {
                            int id = Integer.parseInt(parts[1]);
                            String name = parts[2];
                            String location = parts[3];
                            Hotel hotel = new Hotel(id, name, location);
                            hotelData.insert(hotel);
                            hotels = hotelData.findAll();
                            writer.println("HOTEL_REGISTERED");
                        }

                        case Protocol.REGISTER_ROOM -> {
                            int roomNumber = Integer.parseInt(parts[1]);
                            RoomType type = RoomType.valueOf(parts[2]);
                            int hotelId = Integer.parseInt(parts[3]);
                            String location = parts[4];
                            Hotel hotel = hotelData.findById(hotelId);
                            if (hotel != null) {
                                Room room = new Room(roomNumber, type, hotel, location);
                                roomData.insert(room);
                                writer.println("ROOM_REGISTERED");
                            } else {
                                writer.println("HOTEL_NOT_FOUND");
                            }
                        }

                        case Protocol.GET_ALL_ROOMS -> {
                            List<Room> rooms = roomData.findAll(hotels);
                            StringBuilder rsb = new StringBuilder();
                            for (Room r : rooms) rsb.append(r).append("\n");
                            writer.println(rsb.toString());
                        }

                        case Protocol.ADD_GUEST -> {
//                             parts: ADD_GUEST|name|lastName|gender|id|birthDate
                            // Si tenés GuestData implementado:
                            // Guest g = new Guest(...); guestData.insert(g);
                            String guestName = parts[1];
                            String lastName = parts[2];
                            String gender = parts[3];
                            int id = Integer.parseInt(parts[4]);
                            String birthDate = parts[5];
                            Guest guest=new Guest(guestName,lastName,gender,id,birthDate);
                            guestData.insert(guest);
                            writer.println("GUEST_REGISTERED");
                        }

                        case Protocol.EDIT_HOTELS -> {
                            // parts: EDIT_HOTELS|id|newName|newLocation
                            // Buscar hotel, modificar, guardar de nuevo
                            writer.println("HOTEL_UPDATED");
                        }

                        case Protocol.DELETE_HOTEL -> {
                            // parts: DELETE_HOTEL|id
                            // hotelData.delete(id); implementar método delete
                            writer.println("HOTEL_DELETED");
                        }

                        case Protocol.DELETE_ROOM -> {
                            // parts: DELETE_ROOM|roomNumber
                            // roomData.delete(roomNumber); implementar método delete
                            writer.println("ROOM_DELETED");
                        }

                        case Protocol.EDIT_ROOM -> {
                            // parts: EDIT_ROOM|roomNumber|newType|newLocation
                            // Buscar, modificar, guardar
                            writer.println("ROOM_UPDATED");
                        }

                        default -> writer.println("UNKNOWN_COMMAND");
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
