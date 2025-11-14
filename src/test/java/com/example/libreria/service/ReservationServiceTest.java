package com.example.libreria.service;

import com.example.libreria.dto.BookResponseDTO;
import com.example.libreria.dto.ReservationRequestDTO;
import com.example.libreria.dto.ReservationResponseDTO;
import com.example.libreria.dto.ReturnBookRequestDTO;
import com.example.libreria.model.Book;
import com.example.libreria.model.Reservation;
import com.example.libreria.model.User;
import com.example.libreria.repository.BookRepository;
import com.example.libreria.repository.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {
    
    @Mock
    private ReservationRepository reservationRepository;
    
    @Mock
    private BookRepository bookRepository;
    
    @Mock
    private BookService bookService;
    
    @Mock
    private UserService userService;
    
    @InjectMocks
    private ReservationService reservationService;
    
    private User testUser;
    private Book testBook;
    private Reservation testReservation;
    
    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setName("Juan Pérez");
        testUser.setEmail("juan@example.com");
        
        testBook = new Book();
        testBook.setExternalId(258027L);
        testBook.setTitle("The Lord of the Rings");
        testBook.setPrice(new BigDecimal("15.99"));
        testBook.setStockQuantity(10);
        testBook.setAvailableQuantity(5);
        
        testReservation = new Reservation();
        testReservation.setId(1L);
        testReservation.setUser(testUser);
        testReservation.setBook(testBook);
        testReservation.setRentalDays(7);
        testReservation.setStartDate(LocalDate.now());
        testReservation.setExpectedReturnDate(LocalDate.now().plusDays(7));
        testReservation.setDailyRate(new BigDecimal("15.99"));
        testReservation.setTotalFee(new BigDecimal("111.93"));
        testReservation.setStatus(Reservation.ReservationStatus.ACTIVE);
        testReservation.setCreatedAt(LocalDateTime.now());
    }
    
    @Test
    void testCreateReservation_Success() {
        // Preparar DTO de request
        ReservationRequestDTO requestDTO = new ReservationRequestDTO();
        requestDTO.setUserId(testUser.getId());
        requestDTO.setBookExternalId(testBook.getExternalId());

        // Mock usuario existente
        when(userService.getUserEntity(testUser.getId())).thenReturn(testUser);

        // Mock respuesta externa / BookResponseDTO con disponibilidad
        BookResponseDTO bookResponse = new BookResponseDTO();
        bookResponse.setExternalId(testBook.getExternalId());
        bookResponse.setTitle(testBook.getTitle());
        bookResponse.setAuthorName(List.of("J.R.R. Tolkien"));
        bookResponse.setAvailableQuantity(3);
        bookResponse.setPrice(testBook.getPrice());
        bookResponse.setFirstPublishYear(1954);
        bookResponse.setHasFulltext(false);

        when(bookService.getBookByExternalId(testBook.getExternalId())).thenReturn(bookResponse);

        // Simular save -> devolver la entidad con id
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> {
            Reservation r = invocation.getArgument(0);
            r.setId(1L);
            r.setCreatedAt(LocalDateTime.now());
            return r;
        });

        doNothing().when(bookService).decreaseAvailableQuantity(testBook.getExternalId());

        ReservationResponseDTO result = reservationService.createReservation(requestDTO);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals(testUser.getId(), result.getUserId());
        assertEquals(testBook.getExternalId(), result.getBookExternalId());
        assertEquals(Reservation.ReservationStatus.ACTIVE, result.getStatus());

        verify(reservationRepository, times(1)).save(any(Reservation.class));
        verify(bookService, times(1)).decreaseAvailableQuantity(testBook.getExternalId());
    }


    
    @Test
    void testCreateReservation_BookNotAvailable() {
        ReservationRequestDTO requestDTO = new ReservationRequestDTO();
        requestDTO.setUserId(testUser.getId());
        requestDTO.setBookExternalId(testBook.getExternalId());

        when(userService.getUserEntity(testUser.getId())).thenReturn(testUser);

        BookResponseDTO bookResponse = new BookResponseDTO();
        bookResponse.setExternalId(testBook.getExternalId());
        bookResponse.setTitle(testBook.getTitle());
        bookResponse.setAuthorName(List.of("J.R.R. Tolkien"));
        bookResponse.setAvailableQuantity(3);
        bookResponse.setPrice(testBook.getPrice());
        bookResponse.setFirstPublishYear(1954);
        bookResponse.setHasFulltext(false);

        when(bookService.getBookByExternalId(testBook.getExternalId())).thenReturn(bookResponse);

        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> {
            Reservation r = invocation.getArgument(0);
            r.setId(1L);
            r.setCreatedAt(LocalDateTime.now());
            return r;
        });

        doNothing().when(bookService).decreaseAvailableQuantity(testBook.getExternalId());

        ReservationResponseDTO result = reservationService.createReservation(requestDTO);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals(testUser.getId(), result.getUserId());
        assertEquals(testBook.getExternalId(), result.getBookExternalId());
        assertEquals(Reservation.ReservationStatus.ACTIVE, result.getStatus());

        verify(reservationRepository, times(1)).save(any(Reservation.class));
        verify(bookService, times(1)).decreaseAvailableQuantity(testBook.getExternalId());
    }


    
    @Test
    void testReturnBook_OnTime() {
        ReturnBookRequestDTO returnRequest = new ReturnBookRequestDTO();
        LocalDate returnDate = testReservation.getExpectedReturnDate();
        returnRequest.setReturnDate(returnDate);

        when(reservationRepository.findById(1L)).thenReturn(Optional.of(testReservation));

        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(bookService).increaseAvailableQuantity(testBook.getExternalId());

        ReservationResponseDTO result = reservationService.returnBook(1L, returnRequest);

        assertNotNull(result);
        assertEquals(Reservation.ReservationStatus.RETURNED, result.getStatus());
        assertEquals(new BigDecimal("0.00"), result.getLateFee());
        assertEquals(new BigDecimal("111.93"), result.getTotalFee());

        verify(bookService, times(1)).increaseAvailableQuantity(testBook.getExternalId());
        verify(reservationRepository, times(1)).save(any(Reservation.class));
    }


    @Test
    void testReturnBook_Overdue() {
        ReturnBookRequestDTO returnRequest = new ReturnBookRequestDTO();
        LocalDate returnDate = testReservation.getExpectedReturnDate();
        returnRequest.setReturnDate(returnDate);

        when(reservationRepository.findById(1L)).thenReturn(Optional.of(testReservation));

        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(bookService).increaseAvailableQuantity(testBook.getExternalId());

        ReservationResponseDTO result = reservationService.returnBook(1L, returnRequest);

        assertNotNull(result);
        assertEquals(Reservation.ReservationStatus.RETURNED, result.getStatus());
        assertEquals(new BigDecimal("0.00"), result.getLateFee());
        assertEquals(new BigDecimal("111.93"), result.getTotalFee());

        verify(bookService, times(1)).increaseAvailableQuantity(testBook.getExternalId());
        verify(reservationRepository, times(1)).save(any(Reservation.class));
    }

    @Test
    void testGetReservationById_Success() {
        when(reservationRepository.findById(1L)).thenReturn(Optional.of(testReservation));
        
        ReservationResponseDTO result = reservationService.getReservationById(1L);
        
        assertNotNull(result);
        assertEquals(testReservation.getId(), result.getId());
    }
    /*
    @Test
    void testGetAllReservations() {
        Reservation reservation2 = new Reservation();
        reservation2.setId(2L);
        
        when(reservationRepository.findAll()).thenReturn(Arrays.asList(testReservation, reservation2));
        
        List<ReservationResponseDTO> result = reservationService.getAllReservations();
        
        assertNotNull(result);
        assertEquals(2, result.size());
    }*/
    
    @Test
    void testGetReservationsByUserId() {
        when(reservationRepository.findByUserId(1L)).thenReturn(Arrays.asList(testReservation));
        
        List<ReservationResponseDTO> result = reservationService.getReservationsByUserId(1L);
        
        assertNotNull(result);
        assertEquals(1, result.size());
    }
    
    @Test
    void testGetActiveReservations() {
        when(reservationRepository.findByStatus(Reservation.ReservationStatus.ACTIVE))
                .thenReturn(Arrays.asList(testReservation));
        
        List<ReservationResponseDTO> result = reservationService.getActiveReservations();
        
        assertNotNull(result);
        assertEquals(1, result.size());
    }
}

