package com.ssafy.api.service;

import com.ssafy.api.request.RoomCreatePostRequest;
import com.ssafy.api.response.RoomRes;
import com.ssafy.db.entity.*;
import com.ssafy.db.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class RoomServiceImpl implements RoomService{

    @Autowired
    RoomRepository roomRepository;

    @Autowired
    TagRepository tagRepository;

    @Autowired
    RoomTagRepository roomTagRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    BookCategoryRepository bookCategoryRepository;

    @Autowired
    MovieCategoryRepository movieCategoryRepository;

    @Override
    public List<RoomRes> getRoomList(Long page) {
        List<Room> rooms;
        if( page == 0L ) //기본 페이지가 영화일 때
            rooms = roomRepository.findByMovieCategoryIdGreaterThan(0L);
        else  //기본 페이지가 책일 때
            rooms = roomRepository.findByBookCategoryIdGreaterThan(0L);
        return makeRoomResponseList(rooms);
    }

    @Override
    public Room createRoom(RoomCreatePostRequest roomCreateInfo) {
        Optional<Room> existingRoom = roomRepository.findByRoomTitle(roomCreateInfo.getRoomName());
        if(existingRoom.isPresent()) {
            return null;
        }
        Long bookCategoryId = ( roomCreateInfo.getBookCategoryId() == null)? 0L: roomCreateInfo.getBookCategoryId();
        Long movieCategoryId = ( roomCreateInfo.getMovieCategoryId() == null)? 0L: roomCreateInfo.getMovieCategoryId();
        Room room = Room.builder().roomTitle(roomCreateInfo.getRoomName())
                .hostId(roomCreateInfo.getHostId())
                .roomInviteCode("request_invite_url")
                .movieCategoryId(movieCategoryId)
                .bookCategoryId(bookCategoryId)
                .roomPassword(roomCreateInfo.getPassword())
                .roomImg(roomCreateInfo.getThumbnailUrl())
                .sessionId(roomCreateInfo.getSessionId() + roomCreateInfo.getRoomName()).build();
        addTags(roomCreateInfo.getKeywords(), room);  //키워드가 있다면 db에 추가

        Optional<User> user = userRepository.findByUserId(roomCreateInfo.getHostId());
        if(user.isPresent()) { //hostId로 유저를 찾아서 관계 테이블들에 쏙쏙
            User insertUser = user.get();
            insertUser.getRooms().add(room);
            userRepository.save(insertUser);
      }
        return room;
    }
    @Override
    public void addTags(List<String> keywords, Room room) {
        Optional<Tag> wrappedTag;
        Tag tag;

        for(String keyword: keywords){
            System.out.println("KEYWORD ==== " + keyword);
            wrappedTag = tagRepository.findByTagName(keyword);
            if(wrappedTag.isPresent()) {
                tag = wrappedTag.get();
            }
            else{
                tag = Tag.builder().tagName(keyword).build();
                tagRepository.save(tag); //기존에 없던 태그라면 태그 테이블에 추가.
            }
            RoomTagID roomTagID = new RoomTagID(room.getRoomId(),tag.getTagId());
            RoomTag roomTag = new RoomTag(roomTagID,room,tag);
            roomRepository.save(room);
            roomTagRepository.save(roomTag);
        }
    }

    @Override
    public void deleteRoom(Room selectedRoom) {
        roomRepository.delete(selectedRoom);
    }

    @Override
    public Optional<Room> getRoomByRoomId(Long roomId) {
        return roomRepository.findByRoomId(roomId);
    }

    @Override
    public Optional<Room> getRoomByRoomName(String roomName){
        return roomRepository.findByRoomTitle(roomName);
    }

    @Override
    public RoomRes detailRoom(Long roomId) {
        RoomRes roomRes = null;
        Optional<Room> roomOpt = roomRepository.findByRoomId(roomId);
        if(roomOpt.isPresent()){
            Room room = roomOpt.get();
            Optional<User> userOpt = userRepository.findByUserId(room.getHostId());
            if(userOpt.isPresent()){
                roomRes = makeRoomResponse(room,userOpt.get());
            }
        }
        return roomRes;
    }

    @Override //TO-DO : 유저부분 one-to-many 설정해줘야함? ㅇㅇ - DONE
    public List<RoomRes> getRoomListByHostNickname(String nickname, Long page) {
        List<Room> rooms;
        List<RoomRes> roomResList = new ArrayList<>();
        Optional<User> user = userRepository.findByNickname(nickname);

        if(user.isPresent()){
            User searchedUser = user.get();
            if(page == 0L)
                rooms = roomRepository.findByHostIdAndMovieCategoryIdGreaterThan(searchedUser.getUserId(), 0L);
            else
                rooms = roomRepository.findByHostIdAndBookCategoryIdGreaterThan(searchedUser.getUserId(), 0L);
            for(Room room : rooms){
                RoomRes roomRes = makeRoomResponse(room,searchedUser);
                roomResList.add(roomRes);
            }
        }
        return roomResList;
    }

    @Override
    public List<RoomRes> getRoomListByKeyword(String keyword, Long page) {
        //해당 키워드로 검색
        Optional<Tag> find = tagRepository.findByTagName(keyword);
        List<Room> rooms = new ArrayList<>();
        if(find.isPresent()){
            List<RoomTag> roomTags = roomTagRepository.findRoomTagsByRoomTagIDTagId(find.get().getTagId());
            for(RoomTag roomTag : roomTags){
                // System.out.println("ROOM == " + roomTag.getRoom().getRoomTitle());
                Room room = roomTag.getRoom();
                if(page == 0L) {
                    //영화인 것만 넣는다.
                    if( 0L < room.getMovieCategoryId()) rooms.add(room);
                } else {
                    //책인 것만 넣는다.
                    if( 0L < room.getBookCategoryId()) rooms.add(room);
                }
            }
        }
        return makeRoomResponseList(rooms);
    }
    @Override
    public List<RoomRes> getRoomListByRoomTitle(String roomName, Long page) {
        List<Room> rooms;
        if( page == 0L )
            rooms = roomRepository.findByRoomTitleContainsAndMovieCategoryIdGreaterThan(roomName, 0L);
        else
            rooms =roomRepository.findByRoomTitleContainsAndBookCategoryIdGreaterThan(roomName,0L);
        return makeRoomResponseList(rooms);
    }

//    @Override
//    public Optional<Room> getRoomBySessionId(String sessionId) {
//        return roomRepository.findBySessionId(sessionId);
//    }

    @Override
    public List<RoomRes> getRoomListByMovieId(Long movieId) {
        List<Room> rooms = roomRepository.findByMovieCategoryId(movieId);
        return makeRoomResponseList(rooms);
    }

    @Override
    public List<RoomRes> getRoomListByBookId(Long bookId) {
        List<Room> rooms = roomRepository.findByBookCategoryId(bookId);
        return makeRoomResponseList(rooms);
    }

    @Override
    public boolean checkPassword(String password, String roomName) {
        Optional<Room> room = roomRepository.findByRoomTitle(roomName);
        if( room.isPresent() ){
            Room selectedRoom =room.get();
            System.out.println(password + " same? " + selectedRoom.getRoomPassword());
            return selectedRoom.getRoomPassword().equals(password);
        }
        return false;
    }

    private List<RoomRes> makeRoomResponseList(List<Room> rooms){
        List<RoomRes> roomResList = new ArrayList<>();
        for(Room room : rooms) {
            System.out.println("HOST==" + room.getHostId());
            Optional<User> userOpt = userRepository.findByUserId(room.getHostId());
            userOpt.ifPresent(user -> roomResList.add(makeRoomResponse(room, user)));
        }
        return roomResList;
    }
    private RoomRes makeRoomResponse(Room room, User user) {
        Optional<BookCategory> bookCategory = bookCategoryRepository.findById(room.getBookCategoryId());
        Optional<MovieCategory> movieCategory = movieCategoryRepository.findById(room.getMovieCategoryId());
        List<String> keywords = new ArrayList<>();
        String password = room.getRoomPassword();
        password = (password != null && 0 < password.length())? "Y" : "N";

        RoomRes roomRes = RoomRes.builder()
                .roomId(room.getRoomId())
                .roomName(room.getRoomTitle())
                .hostNickname(user.getNickname())
                .limit(5)
                .password(password)
                .thumbnailUrl(room.getRoomImg())
                .sessionId(room.getSessionId()).build();

        for (RoomTag roomTag : room.getRoomTags()) keywords.add(roomTag.getTag().getTagName());
        roomRes.setKeywords(keywords);

        bookCategory.ifPresent(category -> roomRes.setBookCategory(category.getBookCategory()));
        movieCategory.ifPresent(category -> roomRes.setMovieCategory(category.getMovieCategory()));

        return roomRes;
    }
}
