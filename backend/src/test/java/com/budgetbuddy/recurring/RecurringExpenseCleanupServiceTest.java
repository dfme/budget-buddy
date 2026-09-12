package com.budgetbuddy.recurring;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecurringExpenseCleanupServiceTest {

    @Mock
    private RecurringExpenseRepository recurringExpenseRepository;

    @InjectMocks
    private RecurringExpenseCleanupService recurringExpenseCleanupService;

    @Test
    void deleteAllForUserDeletesRecurringExpenses() {
        recurringExpenseCleanupService.deleteAllForUser(42L);

        verify(recurringExpenseRepository).deleteAllByUserId(42L);
        verifyNoMoreInteractions(recurringExpenseRepository);
    }
}
