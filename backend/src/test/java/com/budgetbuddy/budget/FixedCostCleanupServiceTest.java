package com.budgetbuddy.budget;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FixedCostCleanupServiceTest {

    @Mock
    private FixedCostRepository fixedCostRepository;

    @InjectMocks
    private FixedCostCleanupService fixedCostCleanupService;

    @Test
    void deleteAllForUserDeletesFixedCosts() {
        fixedCostCleanupService.deleteAllForUser(42L);

        verify(fixedCostRepository).deleteAllByUserId(42L);
        verifyNoMoreInteractions(fixedCostRepository);
    }
}
