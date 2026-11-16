package io.github.hyunjyyyy.heavyeqtracker.equipment;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface EquipmentRepository extends JpaRepository<Equipment, Long> {

    /** SELECT … FOR UPDATE. 같은 장비를 바꾸는 트랜잭션은 커밋될 때까지 여기서 기다린다 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Equipment> findWithLockById(Long id);
}
