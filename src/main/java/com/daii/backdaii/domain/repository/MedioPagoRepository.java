package com.daii.backdaii.domain.repository;

import com.daii.backdaii.domain.entity.MedioPago;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface MedioPagoRepository extends JpaRepository<MedioPago, Long> {

    List<MedioPago> findByUsuarioIdAndEliminadoFalseOrderByPrincipalDescCreadoElAsc(Long usuarioId);

    Optional<MedioPago> findFirstByUsuarioIdAndEliminadoFalseOrderByCreadoElAsc(Long usuarioId);

    Optional<MedioPago> findByIdAndUsuarioIdAndEliminadoFalse(Long id, Long usuarioId);

    /** Same card, with its row locked until the transaction ends: for an update checked against If-Match. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<MedioPago> findWithLockByIdAndUsuarioIdAndEliminadoFalse(Long id, Long usuarioId);

    @Query("select m.version from MedioPago m where m.id = :id")
    Optional<Long> findVersionById(Long id);

    long countByUsuarioIdAndEliminadoFalse(Long usuarioId);
}