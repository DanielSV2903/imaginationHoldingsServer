package server;


import com.imaginationHoldings.data.*;
import com.imaginationHoldings.domain.*;
import com.imaginationHoldings.protocol.Protocol;
import com.imaginationHoldings.protocol.Request;
import com.imaginationHoldings.protocol.Response;

import java.io.*;
import java.net.*;
import java.time.LocalDate;
import java.util.*;

public class HotelServer {
    private static HotelData hotelData;
    private static RoomData roomData;
    private static GuestData guestData;
    private static BookingData bookingData;
    private static HotelServiceData hotelServiceData;
    private static List<Hotel> hotels;
    private static List<Guest> guests;


    public static void main(String[] args) throws IOException {
        hotels=new ArrayList<>();
        guests=new ArrayList<>();
        hotelData=new HotelData("C:\\Users\\DanielSV\\Documents\\2025\\proyecto progra2\\imaginationHoldingsServer\\data\\hotels.dat");
        roomData=new RoomData("C:\\Users\\DanielSV\\Documents\\2025\\proyecto progra2\\imaginationHoldingsServer\\data\\rooms.dat");
        guestData=new GuestData("C:\\Users\\DanielSV\\Documents\\2025\\proyecto progra2\\imaginationHoldingsServer\\data\\guests.dat");
        bookingData=new BookingData("C:\\Users\\DanielSV\\Documents\\2025\\proyecto progra2\\imaginationHoldingsServer\\data\\books.dat");
        hotelServiceData=new HotelServiceData(hotelData,roomData,guestData,bookingData);
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
        private int bookSize=1;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }
        @Override
        public void run() {
            try (
                    ObjectOutputStream objectOut = new ObjectOutputStream(socket.getOutputStream());
                    ObjectInputStream input = new ObjectInputStream(socket.getInputStream())
            ) {


                try {
                    while (true) {
                        Object inputObject = input.readObject();

                        if (!(inputObject instanceof Request request)) {
                            continue;
                        }

                        String command = request.getCommand();
                        String[] parameters = request.getParameters();
                        String[] parts = parameters.length > 0 ? parameters[0].split("\\|") : new String[0];

                        Object data = request.getData();

                        switch (command) {
                            case Protocol.GET_ALL_HOTELS -> {
                                objectOut.writeObject(hotels);
                                objectOut.flush();
                            }

                            case Protocol.REGISTER_HOTEL -> {
                                int id = Integer.parseInt(parts[1]);
                                String name = parts[2];
                                String location = parts[3];
                                Hotel hotel = new Hotel(id, name, location);
                                hotelData.insert(hotel);
                                hotels = hotelData.findAll();
                                Response response=new Response("HOTEL REGISTERED");
                                objectOut.writeObject(response);
                            }

                            case Protocol.REGISTER_ROOM -> {
                                Room room = (data instanceof Room r) ? r : null;
                                Hotel hotel = hotelData.findById(room.getHotel().getId());
                                if (hotel != null) {
                                    roomData.insert(room);
                                    objectOut.writeObject("ROOM_REGISTERED");
                                    objectOut.flush();
                                } else {
                                    objectOut.writeObject("HOTEL_NOT_FOUND");
                                    objectOut.flush();
                                }
                            }

                            case Protocol.GET_ALL_ROOMS -> {
                                List<Room> rooms = roomData.findAll(hotels);
                                objectOut.writeObject(rooms);
                                objectOut.flush();
                            }
                            case Protocol.GET_BOOKINGS->{
                                List<Booking> bookings=bookingData.findAll();
                                objectOut.writeObject(bookings);
                                objectOut.flush();
                            }

                            case Protocol.ADD_GUEST -> {
                                String guestName = parts[1];
                                String lastName = parts[2];
                                String gender = parts[3];
                                int id = Integer.parseInt(parts[4]);
                                String birthDate = parts[5];
                                Guest guest = new Guest(guestName, lastName, gender, id, birthDate);
                                guestData.insert(guest);
                                objectOut.writeObject("GUEST_REGISTERED");
                                objectOut.flush();
                            }

                            case Protocol.EDIT_HOTELS -> {//TODO
                                Hotel hotel= (Hotel) data;
                                boolean updated=hotelData.update(hotel);
                                if (updated) {
                                    Response response=new Response("HOTEL_UPDATED");
                                    objectOut.writeObject(response);
                                    objectOut.flush();
                                }
                            }

                            case Protocol.DELETE_HOTEL -> {
                                int id= (int) data;
                                hotelData.delete(id);
                                Response response=new Response("HOTEL_DELETED");
                                objectOut.writeObject(response);
                                objectOut.flush();
                            }

                            case Protocol.DELETE_ROOM -> {//TODO
                                Room room = (data instanceof Room r) ? r : null;
                                boolean deleted=roomData.delete(room.getRoomNumber());
                                Response response=new Response("ROOM_DELETED");
                                if (deleted) {
                                objectOut.writeObject(response);
                                objectOut.flush();
                                } else {
                                    response=new Response("ROOM_NOT_FOUND");
                                    objectOut.writeObject(response);
                                    objectOut.flush();
                                }
                            }

                            case Protocol.EDIT_ROOM -> {//TODO
                                Room room= (Room) data;
                                roomData.update(room);
                                Response response=new Response("ROOM_UPDATED");
                                objectOut.writeObject(response);
                                objectOut.flush();
                            }

                            case Protocol.RESERVE_ROOM -> {
                                String name = parts[1];
                                String lastName = parts[2];
                                String checkIn = parts[3];
                                String checkOut = parts[4];
                                int id = Integer.parseInt(parts[5]);
                                String type = parts[6];
                                RoomType roomType = RoomType.SINGLE;
                                for (RoomType rType : RoomType.values()) {
                                    if (rType.getDescription().equals(type)) {
                                        roomType = rType;
                                        break;
                                    }
                                }
                                int guestAmount = Integer.parseInt(parts[7]);
                                StayPeriod stayPeriod = new StayPeriod(LocalDate.parse(checkIn), LocalDate.parse(checkOut));
                                Guest guest = hotelServiceData.findGuestById(id);
                                guest.setFirstName(name);
                                guest.setLastName(lastName);
                                Room room = hotelServiceData.findAvaibleRoom(roomType);
                                int size= bookingData.findAll().size();
                                Booking booking = new Booking( size+1, room, guest, guestAmount, stayPeriod);
                                bookingData.insert(booking);
                                Response response=new Response("BOOKING_DONE");
                                objectOut.writeObject(response);
                                objectOut.flush();
                            }
                            case Protocol.CANCEL_RESERVATION ->{
                                Booking booking = (data instanceof Booking) ? (Booking) data : null;
                                boolean deleted= bookingData.delete(booking.getId());
                                Response response;
                                if (deleted) {
                                    List<Booking> bookings=bookingData.findAll();
                                     response=new Response("BOOKING_DELETED");
                                }else response=new Response("BOOKING_NOT_FOUND");
                                objectOut.writeObject(response);
                                objectOut.flush();
                            }

                            default -> {
                                Response response=new Response("UNKNOWN_COMMAND");
                                objectOut.writeObject(response);
                                objectOut.flush();
                            }
                        }
                    }
                } catch (EOFException eof) {
                    System.out.println("Cliente cerró la conexión.");
                }

            } catch (IOException | ClassNotFoundException | RoomException e) {
                e.printStackTrace();
            }
        }
    }
}
//        @Override
//        public void run() {
//            try (
//                    ObjectOutputStream objectOut = new ObjectOutputStream(socket.getOutputStream());
//                    ObjectInputStream input = new ObjectInputStream(socket.getInputStream());
//
//
//            ) {
//                preloadHotels();
////                String request;
////                while ((request = input.readLine()) != null) {
////                    String[] parts = request.split("\\|");
////                    String command = parts[0];
//                while (true) {
//                    Object inputObject = input.readObject();
//
//                    if (!(inputObject instanceof Request request)) {
//                        continue;
//                    }
//                        String command = request.getCommand();
//                        String[] parts = request.getParameters();
//                        Object data= request.getData();
//
//                    switch (command) {
//                        case Protocol.GET_ALL_HOTELS -> {
//                            objectOut.writeObject(hotels);
//                            objectOut.flush();
//                            break;
//                        }
//
//                        case Protocol.REGISTER_HOTEL -> {
//                            int id = Integer.parseInt(parts[1]);
//                            String name = parts[2];
//                            String location = parts[3];
//                            Hotel hotel = new Hotel(id, name, location);
//                            hotelData.insert(hotel);
//                            hotels = hotelData.findAll();
//
//                        }
//
//                        case Protocol.REGISTER_ROOM -> {
//                            Room room=null;
//                            if (data instanceof Room) room=(Room)data;
//
//                            Hotel hotel = hotelData.findById(room.getHotel().getId());
//                            if (hotel != null) {
//                                roomData.insert(room);
//
//                            } else {
//                                objectOut.writeObject("HOTEL_NOT_FOUND");
//                            }
//                        }
//
//                        case Protocol.GET_ALL_ROOMS -> {
//                            List<Room> rooms = roomData.findAll(hotels);
//                            objectOut.writeObject(rooms);
//                            objectOut.flush();
//                            objectOut.writeObject("OK");
//                            break;
//                        }
//
//                        case Protocol.ADD_GUEST -> {
////                             parts: ADD_GUEST|name|lastName|gender|id|birthDate
//                            // Si tenés GuestData implementado:
//                            // Guest g = new Guest(...); guestData.insert(g);
//                            String guestName = parts[1];
//                            String lastName = parts[2];
//                            String gender = parts[3];
//                            int id = Integer.parseInt(parts[4]);
//                            String birthDate = parts[5];
//                            Guest guest=new Guest(guestName,lastName,gender,id,birthDate);
//                            guestData.insert(guest);
//                            objectOut.writeObject("GUEST_REGISTERED");
//                        }
//
//                        case Protocol.EDIT_HOTELS -> {
//                            // parts: EDIT_HOTELS|id|newName|newLocation
//                            // Buscar hotel, modificar, guardar de nuevo
//                            objectOut.writeObject("HOTEL_UPDATED");
//                        }
//
//                        case Protocol.DELETE_HOTEL -> {
//                            // parts: DELETE_HOTEL|id
//                            // hotelData.delete(id); implementar método delete
//                            objectOut.writeObject("HOTEL_DELETED");
//                        }
//
//                        case Protocol.DELETE_ROOM -> {
//                            // parts: DELETE_ROOM|roomNumber
//                            // roomData.delete(roomNumber); implementar método delete
//                            objectOut.writeObject("ROOM_DELETED");
//                        }
//
//                        case Protocol.EDIT_ROOM -> {
//                            // parts: EDIT_ROOM|roomNumber|newType|newLocation
//                            // Buscar, modificar, guardar
//                            objectOut.writeObject("ROOM_UPDATED");
//                        }
//                        case Protocol.RESERVE_ROOM ->{
//                            String name = parts[1];
//                            String lastName = parts[2];
//                            String checkIn = parts[3];
//                            String checkOut = parts[4];
//                            int id = Integer.parseInt(parts[5]);
//                            String type = parts[6];
//                            RoomType roomType=RoomType.SINGLE;
//                            for (RoomType rType : RoomType.values()) {
//                                if (rType.getDescription().equals(type)) {
//                                    roomType = rType;
//                                    break;
//                                }
//                            }
//                            int guestAmount= Integer.parseInt(parts[7]);
//                            StayPeriod stayPeriod=new StayPeriod(LocalDate.parse(checkIn),LocalDate.parse(checkOut));
//                            Guest guest= hotelServiceData.findGuestById(id);
//                            guest.setFirstName(name);
//                            guest.setLastName(lastName);
//                            Room room=hotelServiceData.findAvaibleRoom(roomType);
//                            try {
//                                Booking booking=new Booking(bookingData.findAll().size()+1,room,guest,guestAmount,stayPeriod);
//                                bookingData.insert(booking);
//                                objectOut.writeObject("BOOKING_DONE");
//                            } catch (RoomException e) {
//                                throw new RuntimeException(e);
//                            }
//                        }
//
//
//                        default -> objectOut.writeObject("UNKNOWN_COMMAND");
//                    }
//                }
//            } catch (IOException e) {
//                e.printStackTrace();
//            } catch (ClassNotFoundException e) {
//                throw new RuntimeException(e);
//            }
//        }
//    }
