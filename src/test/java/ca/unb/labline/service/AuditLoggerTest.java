package ca.unb.labline.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import ca.unb.labline.model.AuditLog;
import ca.unb.labline.model.RequestStatus;
import ca.unb.labline.model.User;
import ca.unb.labline.repository.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Each status change produces exactly one audit row with the right details. */
@ExtendWith(MockitoExtension.class)
class AuditLoggerTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AuditLogger auditLogger;

    @Test
    void recordSavesExactlyOneRowWithAllDetails() {
        User student = new User("test.student@unb.ca", "Test Student", "HASH");

        auditLogger.record(null, RequestStatus.WAITING, RequestStatus.CANCELLED,
                student, "Cancelled by student");

        ArgumentCaptor<AuditLog> saved = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository, times(1)).save(saved.capture());
        assertEquals(RequestStatus.WAITING, saved.getValue().getOldStatus());
        assertEquals(RequestStatus.CANCELLED, saved.getValue().getNewStatus());
        assertEquals(student, saved.getValue().getChangedBy());
        assertEquals("Cancelled by student", saved.getValue().getReason());
        assertNotNull(saved.getValue().getChangedAt());
    }
}
