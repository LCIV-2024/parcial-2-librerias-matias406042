package com.example.libreria.repository;

import com.example.libreria.dto.ReservationResponseDTO;
import com.example.libreria.model.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    
    List<Reservation> findByUserId(Long userId);

    List<Reservation> findByStatus(Reservation.ReservationStatus status);



    List<Reservation> findByStatusAndExpectedReturnDateBefore(String status, LocalDate date);

}
