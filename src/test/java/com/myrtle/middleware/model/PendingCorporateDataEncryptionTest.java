package com.myrtle.middleware.model;

import com.myrtle.middleware.model.converter.EncryptedStringConverter;
import com.myrtle.middleware.repository.PendingCorporateDataRepository;
import com.myrtle.middleware.utils.FieldEncryptor;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the encryption is actually wired into Hibernate - a hand-constructed converter passing in
 * a unit test would not catch a dependency-injection failure at runtime.
 */
@DataJpaTest
@Import({FieldEncryptor.class, EncryptedStringConverter.class})
@EntityScan(basePackages = "com.myrtle.middleware.model")
@TestPropertySource(properties = {
        "app.encryption.key=Zml4bWUtbG9jYWwtb25seS1rZXktbm90LWZvci1wcm9kIQ==",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=dGVzdC1zZWNyZXQtdGhhdC1pcy1sb25nLWVub3VnaC1mb3ItaG1hYw==",
        "DB_URL=unused",
        "DB_USERNAME=unused",
        "DB_PASSWORD=unused",
        "ENVIRONMENT=test"
})
class PendingCorporateDataEncryptionTest {

    private static final String PASSWORD = "Password123!";
    private static final String PIN = "8675";

    @Autowired private PendingCorporateDataRepository repository;
    @Autowired private EntityManager entityManager;

    private String persistOnboarding() {
        PendingCorporateData pending = new PendingCorporateData();
        pending.setEmail("owner@company.com");
        pending.setFirstName("David");
        pending.setLastName("Areegbe");
        pending.setPassword(PASSWORD);
        pending.setPin(PIN);

        PendingCorporateData saved = repository.saveAndFlush(pending);
        entityManager.clear();
        return saved.getId();
    }

    @Test
    void pinAndPasswordAreCiphertextInTheDatabase() {
        String id = persistOnboarding();

        Object[] stored = (Object[]) entityManager
                .createNativeQuery("select password, pin from pending_corporate_data where id = :id")
                .setParameter("id", id)
                .getSingleResult();

        String storedPassword = (String) stored[0];
        String storedPin = (String) stored[1];

        assertThat(storedPassword).startsWith("enc:v1:").doesNotContain(PASSWORD);
        assertThat(storedPin).startsWith("enc:v1:").doesNotContain(PIN);
    }

    @Test
    void theyComeBackAsPlaintextSoTheMyWealthCallStillWorks() {
        String id = persistOnboarding();

        Optional<PendingCorporateData> reloaded = repository.findById(id);

        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getPassword()).isEqualTo(PASSWORD);
        assertThat(reloaded.get().getPin()).isEqualTo(PIN);
    }

    @Test
    void nonSensitiveColumnsAreLeftAlone() {
        String id = persistOnboarding();

        String storedEmail = (String) entityManager
                .createNativeQuery("select email from pending_corporate_data where id = :id")
                .setParameter("id", id)
                .getSingleResult();

        assertThat(storedEmail).isEqualTo("owner@company.com");
    }
}
