package org.eclipse.edc.vault.azure;

import org.eclipse.edc.junit.extensions.DependencyInjectionExtension;
import org.eclipse.edc.spi.security.Vault;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.system.injection.ObjectFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Disabled
@ExtendWith(DependencyInjectionExtension.class)
public class Sandbox {

    private static final String VAULT_NAME_SETTING = "edc.vault.name";
    //    private static final String VAULT_NAME = runtimeSettingsProperties().getProperty("test.key.vault.name");

    private ServiceExtensionContext context;
    private AzureVaultExtension extension;

    @BeforeEach
    void setUp(ServiceExtensionContext context, ObjectFactory factory) {
        this.context = context;
        extension = factory.constructInstance(AzureVaultExtension.class);
    }

    @DisplayName("Assert creation and deletion of secret in Azure Key Vault")
    @Test
    void test() {

        extension.initialize(context);
        var secretName = UUID.randomUUID().toString();
        var secretValue = UUID.randomUUID().toString();

        var vault = context.getService(Vault.class);

        assertThat(vault).isInstanceOf(AzureVault.class);

        var storeResult = vault.storeSecret(secretName, secretValue);
        assertThat(storeResult.succeeded()).isTrue();

        var resolveResult = vault.resolveSecret(secretName);
        assertThat(resolveResult).isNotNull()
                .isEqualTo(secretValue);

        var deleteResult = vault.deleteSecret(secretName);
        assertThat(deleteResult.succeeded()).isTrue();

        assertThat(vault.resolveSecret(secretName)).isNull();
    }

    @BeforeAll
    static void setProps() {
        System.setProperty(VAULT_NAME_SETTING, VAULT_NAME);
    }

    @AfterAll
    static void unsetProps() {
        System.clearProperty(VAULT_NAME_SETTING);
    }

}
