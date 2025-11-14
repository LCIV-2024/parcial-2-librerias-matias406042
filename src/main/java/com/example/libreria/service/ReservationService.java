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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReservationService {
    
    private static final BigDecimal LATE_FEE_PERCENTAGE = new BigDecimal("0.15"); // 15% por día
    
    private final ReservationRepository reservationRepository;
    private final BookRepository bookRepository;
    private final BookService bookService;
    private final UserService userService;
    
    @Transactional
    public ReservationResponseDTO createReservation(ReservationRequestDTO requestDTO) {

        Reservation reservation = new Reservation();
        // Validar que el usuario existe
        User user = userService.getUserEntity(requestDTO.getUserId());
        reservation.setUser(user);
        // Validar que el libro existe y está disponible
        BookResponseDTO bookResponseDTO = bookService.getBookByExternalId(requestDTO.getBookExternalId());
        Book bookEntity = new Book();
        bookEntity.setAuthorName(bookResponseDTO.getAuthorName());
        bookEntity.setTitle(bookResponseDTO.getTitle());
        bookEntity.setExternalId(bookResponseDTO.getExternalId());
        bookEntity.setAvailableQuantity(bookResponseDTO.getAvailableQuantity());
        bookEntity.setPrice(bookResponseDTO.getPrice());
        bookEntity.setFirstPublishYear(bookResponseDTO.getFirstPublishYear());
        bookEntity.setHasFulltext(bookResponseDTO.getHasFulltext());
        if (bookEntity.getAvailableQuantity() <= 0) {
            throw new RuntimeException("El libro no está disponible para reserva");
        }

        reservation.setUser(user);
        reservation.setBook(bookEntity);
        reservation.setRentalDays(requestDTO.getRentalDays());
        reservation.setStartDate(requestDTO.getStartDate());
        reservation.setExpectedReturnDate(requestDTO.getStartDate().plusDays(requestDTO.getRentalDays()));
        reservation.setDailyRate(bookEntity.getPrice());
        reservation.setTotalFee(calculateTotalFee(bookEntity.getPrice(), requestDTO.getRentalDays()));
        reservation.setStatus(Reservation.ReservationStatus.ACTIVE);

        // Crear la reserva
        Reservation saved= reservationRepository.save(reservation);
        // Reducir la cantidad disponible
        bookService.decreaseAvailableQuantity(saved.getBook().getExternalId());

        return convertToDTO(saved);

    }
    
    @Transactional
    public ReservationResponseDTO returnBook(Long reservationId, ReturnBookRequestDTO returnRequest) {

        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new RuntimeException("Reserva no encontrada con ID: " + reservationId));
        
        if (reservation.getStatus() != Reservation.ReservationStatus.ACTIVE) {
            throw new RuntimeException("La reserva ya fue devuelta");
        }
        
        LocalDate returnDate = returnRequest.getReturnDate();
        reservation.setActualReturnDate(returnDate);
        
        // Calcular tarifa por demora si hay retraso
        LocalDate expected = reservation.getExpectedReturnDate();
        long daysLate = 0;
        if (expected != null && returnDate != null && returnDate.isAfter(expected)) {
            daysLate = ChronoUnit.DAYS.between(expected, returnDate);
        }
        BigDecimal lateFee = calculateLateFee(reservation.getBook() != null ? reservation.getBook().getPrice() : BigDecimal.ZERO, daysLate);
        reservation.setLateFee(lateFee);

        // Calcular tarifa total (diaria * días de renta) + multa
        BigDecimal dailyRate = reservation.getDailyRate() != null ? reservation.getDailyRate() : BigDecimal.ZERO;
        Integer rentalDays = reservation.getRentalDays() != null ? reservation.getRentalDays() : 0;
        BigDecimal totalFee = calculateTotalFee(dailyRate, rentalDays).add(lateFee).setScale(2, RoundingMode.HALF_UP);
        reservation.setTotalFee(totalFee);

        // Actualizar estado
        reservation.setStatus(Reservation.ReservationStatus.RETURNED);

        // Aumentar la cantidad disponible
        if (reservation.getBook() != null && reservation.getBook().getExternalId() != null) {
            bookService.increaseAvailableQuantity(reservation.getBook().getExternalId());
        }

        Reservation saved = reservationRepository.save(reservation);
        return convertToDTO(saved);

    }
    
    @Transactional(readOnly = true)
    public ReservationResponseDTO getReservationById(Long id) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Reserva no encontrada con ID: " + id));
        return convertToDTO(reservation);
    }
    
    @Transactional(readOnly = true)
    public List<ReservationResponseDTO> getAllReservations() {
        return reservationRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public List<ReservationResponseDTO> getReservationsByUserId(Long userId) {
        return reservationRepository.findByUserId(userId).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public List<ReservationResponseDTO> getActiveReservations() {
        return reservationRepository.findByStatus(Reservation.ReservationStatus.ACTIVE).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public List<ReservationResponseDTO> getOverdueReservations() {

        return reservationRepository.findByStatusAndExpectedReturnDateBefore(Reservation.ReservationStatus.ACTIVE, LocalDate.now()).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());

    }
    
    private BigDecimal calculateTotalFee(BigDecimal dailyRate, Integer rentalDays) {

        if (dailyRate == null || rentalDays == null || rentalDays <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return dailyRate.multiply(BigDecimal.valueOf(rentalDays)).setScale(2, RoundingMode.HALF_UP);
    }
    
    private BigDecimal calculateLateFee(BigDecimal bookPrice, long daysLate) {
        if (bookPrice == null || daysLate <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal perDay = bookPrice.multiply(LATE_FEE_PERCENTAGE);
        return perDay.multiply(BigDecimal.valueOf(daysLate)).setScale(2, RoundingMode.HALF_UP);
    }
    
    private ReservationResponseDTO convertToDTO(Reservation reservation) {
        ReservationResponseDTO dto = new ReservationResponseDTO();
        dto.setId(reservation.getId());
        dto.setUserId(reservation.getUser().getId());
        dto.setUserName(reservation.getUser().getName());
        dto.setBookExternalId(reservation.getBook().getExternalId());
        dto.setBookTitle(reservation.getBook().getTitle());
        dto.setRentalDays(reservation.getRentalDays());
        dto.setStartDate(reservation.getStartDate());
        dto.setExpectedReturnDate(reservation.getExpectedReturnDate());
        dto.setActualReturnDate(reservation.getActualReturnDate());
        dto.setDailyRate(reservation.getDailyRate());
        dto.setTotalFee(reservation.getTotalFee());
        dto.setLateFee(reservation.getLateFee());
        dto.setStatus(reservation.getStatus());
        dto.setCreatedAt(reservation.getCreatedAt());
        return dto;
    }
}

