package com.trabalhaki.backend.repository;

import com.trabalhaki.backend.domain.model.SelectionProcess;
import com.trabalhaki.backend.domain.model.SelectionProcessHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SelectionProcessHistoryRepository extends JpaRepository<SelectionProcessHistory, Long> {

    List<SelectionProcessHistory> findBySelectionProcessOrderByChangedAtAsc(SelectionProcess selectionProcess);

    List<SelectionProcessHistory> findBySelectionProcessIdOrderByChangedAtAsc(Long selectionProcessId);
}
