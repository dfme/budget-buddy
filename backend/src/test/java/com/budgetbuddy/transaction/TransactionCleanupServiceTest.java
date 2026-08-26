package com.budgetbuddy.transaction;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionCleanupServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private ImportJobRepository importJobRepository;

    @InjectMocks
    private TransactionCleanupService transactionCleanupService;

    @Test
    void deleteAllForUserDeletesTransactionsAndImportJobs() {
        transactionCleanupService.deleteAllForUser(42L);

        InOrder order = inOrder(transactionRepository, importJobRepository);
        order.verify(transactionRepository).deleteAllByUserId(42L);
        order.verify(importJobRepository).deleteAllByUserId(42L);
        verifyNoMoreInteractions(transactionRepository, importJobRepository);
    }
}
