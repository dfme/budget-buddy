package com.budgetbuddy.categorization;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CategoryLookupCleanupServiceTest {

    @Mock
    private UserCategoryLookupRepository userCategoryLookupRepository;

    @InjectMocks
    private CategoryLookupCleanupService categoryLookupCleanupService;

    @Test
    void deleteAllForUserDeletesLearnedPatterns() {
        categoryLookupCleanupService.deleteAllForUser(42L);

        verify(userCategoryLookupRepository).deleteAllByUserId(42L);
        verifyNoMoreInteractions(userCategoryLookupRepository);
    }
}
