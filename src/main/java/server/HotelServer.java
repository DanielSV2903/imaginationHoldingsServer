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
        hotels = new ArrayList<>();
        guests = new ArrayList<>();
        hotelData = new HotelData("C:\\Users\\DanielSV\\Documents\\2025\\proyecto progra2\\imaginationHoldingsServer\\data\\hotels.dat");
        roomData = new RoomData("C:\\Users\\DanielSV\\Documents\\2025\\proyecto progra2\\imaginationHoldingsServer\\data\\rooms.dat");
        guestData = new GuestData("C:\\Users\\DanielSV\\Documents\\2025\\proyecto progra2\\imaginationHoldingsServer\\data\\guests.dat");
        bookingData = new BookingData("C:\\Users\\DanielSV\\Documents\\2025\\proyecto progra2\\imaginationHoldingsServer\\data\\books.dat");
        hotelServiceData = new HotelServiceData(hotelData, roomData, guestData, bookingData);
        preloadHotels();
        ServerSocket serverSocket = new ServerSocket(5000);
        System.out.println("Servidor escuchando en puerto 5000...");

        while (true) {
            Socket clientSocket = serverSocket.accept();
            new Thread(new ClientHandler(clientSocket)).start();
        }
    }

    private static void preloadHotels() throws IOException {
        hotels = hotelData.findAll();
    }


    private static class ClientHandler implements Runnable {
        private Socket socket;
        private int bookSize = 1;

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
                                List<Hotel> hotelList = hotelData.findAll();
                                hotels = hotelList;
                                objectOut.writeObject(hotelList);
                                objectOut.flush();
                            }

                            case Protocol.REGISTER_HOTEL -> {
                                int id = Integer.parseInt(parts[1]);
                                String name = parts[2];
                                String location = parts[3];
                                Hotel hotel = new Hotel(id, name, location);

                                Response response;

                                if (hotelData.findById(id) != null) {
                                    response = new Response("HOTEL_ALREADY_EXISTS");
                                } else {
                                    hotelData.insert(hotel);
                                    hotels = hotelData.findAll();
                                    response = new Response("HOTEL_REGISTERED");
                                }
                                objectOut.writeObject(response);
                            }

                            case Protocol.REGISTER_ROOM -> {
                                Room room = (data instanceof Room r) ? r : null;
                                Hotel hotel = hotelData.findById(room.getHotel().getId());
                                if (hotel != null) {
                                    List<Room> rooms = hotel.getRooms();
                                    boolean exists = false;
                                    for (Room roomActual : rooms) {
                                        if (roomActual.getRoomNumber() == room.getRoomNumber()) {
                                            objectOut.writeObject(Response.ROOM_ALREADY_EXISTS);
                                            exists = true;
                                            break;
                                        }
                                    }
                                    if (!exists) {
                                        roomData.insert(room);
                                        objectOut.writeObject(Response.HOTEL_REGISTERED);
                                        objectOut.flush();
                                    }
                                } else {
                                    objectOut.writeObject(Response.HOTEL_NOT_FOUND);
                                    objectOut.flush();
                                }
                            }

                            case Protocol.GET_ALL_ROOMS -> {
                                List<Room> rooms = roomData.findAll(hotels);
                                objectOut.writeObject(rooms);
                                objectOut.flush();
                            }
                            case Protocol.GET_BOOKINGS -> {
                                List<Booking> bookings = bookingData.findAll();
                                objectOut.writeObject(bookings);
                                objectOut.flush();
                            }
                            case Protocol.GET_ALL_GUESTS -> {
                                List<Guest> guests = guestData.findAll();
                                objectOut.writeObject(guests);
                                objectOut.flush();
                            }

                            case Protocol.ADD_GUEST -> {
                                String guestName = parts[1];
                                String lastName = parts[2];
                                String gender = parts[3];
                                int id = Integer.parseInt(parts[4]);
                                String birthDate = parts[5];
                                Guest guest = new Guest(guestName, lastName, gender, id, birthDate);

                                if (guestData.findById(guest.getId()) != null) {
                                    objectOut.writeObject(Response.GUEST_ALREADY_EXISTS);
                                } else {
                                    guestData.insert(guest);
                                    objectOut.writeObject(Response.GUEST_REGISTERED);
                                }

                                objectOut.flush();
                            }
                            case Protocol.EDIT_GUEST -> {
                                Guest guest = (data instanceof Guest) ? (Guest) data : null;
                                guestData.update(guest);
                                Response response = new Response(Response.GUEST_UPDATED);
                                objectOut.writeObject(response);
                                objectOut.flush();
                            }

                            case Protocol.DELETE_GUEST -> {
                                Guest guest = (data instanceof Guest) ? (Guest) data : null;
                                guestData.delete(guest.getId());
                                Response response = new Response(Response.GUEST_DELETED);
                                objectOut.writeObject(response);
                                objectOut.flush();
                            }

                            case Protocol.EDIT_HOTELS -> {//TODO
                                Hotel hotel = (Hotel) data;
                                boolean updated = hotelData.update(hotel);
                                if (updated) {
                                    Response response = new Response(Response.HOTEL_UPDATED);
                                    objectOut.writeObject(response);
                                    objectOut.flush();
                                }
                            }
                            case Protocol.EDIT_RESERVATION -> {
                                Booking booking = (data instanceof Booking) ? (Booking) data : null;
                                booking.getRoom().setHotel(new Hotel(bookingData.findById(booking.getId()).getRoom().getHotel().getId()));
                                boolean updated = bookingData.update(booking);
                                if (updated) {
                                    Response response = new Response(Response.BOOKING_DONE);
                                    objectOut.writeObject(response);
                                    objectOut.flush();
                                }
                            }

                            case Protocol.DELETE_HOTEL -> {
                                int id = (int) data;
                                Hotel h = hotelServiceData.findHotelById(id);
                                List<Room> rooms = h.getRooms();
                                for (Room room : rooms) {
                                    roomData.delete(room.getRoomNumber(), id);
                                }
                                hotelData.delete(id);
                                Response response = new Response(Response.HOTEL_DELETED);
                                objectOut.writeObject(response);
                                objectOut.flush();
                            }

                            case Protocol.DELETE_ROOM -> {//TODO
                                Room room = (data instanceof Room r) ? r : null;
                                boolean deleted = roomData.delete(room.getRoomNumber());
                                Response response = new Response(Response.ROOM_DELETED);
                                if (deleted) {
                                    objectOut.writeObject(response);
                                    objectOut.flush();
                                } else {
                                    response = new Response(Response.ROOM_NOT_FOUND);
                                    objectOut.writeObject(response);
                                    objectOut.flush();
                                }
                            }

                            case Protocol.EDIT_ROOM -> {//TODO
                                Room room = (Room) data;
                                roomData.update(room);
                                Response response = new Response(Response.ROOM_UPDATED);
                                objectOut.writeObject(response);
                                objectOut.flush();
                            }

                            case Protocol.RESERVE_ROOM -> {
                                String mode = parts[0];
                                switch (mode) {
                                    case "1":
                                        String name = parts[1];
                                        String lastName = parts[2];
                                        String checkIn = parts[3];
                                        String checkOut = parts[4];
                                        int id = Integer.parseInt(parts[5]);
                                        String type = parts[6];
                                        int hotelID = Integer.parseInt(parts[7]);
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
                                        if (guest == null) {
                                            guest = new Guest(id);
                                            guest.setFirstName(name);
                                            guest.setLastName(lastName);
                                        }
                                        Room room = hotelServiceData.findAvaibleRoom(roomType);
                                        room.setHotel(new Hotel(hotelID));
                                        int size = bookingData.findAll().size();
                                        Booking booking = new Booking(size + 1, room, guest, guestAmount, stayPeriod);
                                        bookingData.insert(booking);
                                        Response response = new Response(Response.BOOKING_DONE);

                                        objectOut.writeObject(response);
                                        objectOut.flush();
                                        break;
                                    case "2":
                                        name = parts[1];
                                        lastName = parts[2];
                                        checkIn = parts[3];
                                        checkOut = parts[4];
                                        id = Integer.parseInt(parts[5]);
                                        int roomNumber = Integer.parseInt(parts[6]);
                                        guestAmount = Integer.parseInt(parts[7]);
                                        int hotel=Integer.parseInt(parts[8]);
                                        stayPeriod = new StayPeriod(LocalDate.parse(checkIn), LocalDate.parse(checkOut));
                                        guest = hotelServiceData.findGuestById(id);
                                        guest.setFirstName(name);
                                        guest.setLastName(lastName);
                                        room = hotelServiceData.findRoomByID(roomNumber);
                                        Hotel h=hotelServiceData.findHotelById(hotel);
                                        room.setHotel(h);
                                        boolean avl = bookingData.checkAvailabilityOnSP(roomNumber, room.getHotel().getId(), stayPeriod);
                                        if (avl) {
                                            size = bookingData.findAll().size();
                                            booking = new Booking(size + 1, room, guest, guestAmount, stayPeriod);
                                            bookingData.insert(booking);
                                            response = new Response(Response.BOOKING_DONE);
                                            objectOut.writeObject(response);
                                            objectOut.flush();
                                        } else {
                                            response = new Response("ROOM_UNAVAILABLE");
                                            objectOut.writeObject(response);
                                            objectOut.flush();
                                        }
                                        break;
                                    default:
                                        break;
                                }

                            }
                            case Protocol.CANCEL_RESERVATION -> {
                                Booking booking = (data instanceof Booking) ? (Booking) data : null;
                                boolean deleted = bookingData.delete(booking.getId());
                                Response response;
                                if (deleted) {
                                    List<Booking> bookings = bookingData.findAll();
                                    response = new Response(Response.BOOKING_DELETED);
                                } else response = new Response(Response.BOOKING_NOT_FOUND);
                                objectOut.writeObject(response);
                                objectOut.flush();
                            }
                            case Protocol.CHECK_AVAILABILITY_BY_STAY_PERIOD -> {
                                Object[] dataArray = (data instanceof Object[]) ? (Object[]) data : null;
                                StayPeriod stayPeriod = (StayPeriod) dataArray[0];
                                int roomNumber = (int) dataArray[1];
                                int hotelID= (int) dataArray[2];
//                                Room room = hotelServiceData.findRoomByID(roomNumber);
                                boolean availability = bookingData.checkAvailabilityOnSP(roomNumber, hotelID, stayPeriod);
                                objectOut.writeBoolean(availability);
                                objectOut.flush();
                            }

                            default -> {
                                Response response = new Response(Response.UNKNOWN_COMMAND);
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