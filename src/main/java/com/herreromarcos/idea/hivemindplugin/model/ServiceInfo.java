package com.herreromarcos.idea.hivemindplugin.model;

import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.herreromarcos.idea.hivemindplugin.HiveMindConstants.*;

/**
 * Ein Service ("Bean") mit allem, was HiveMind beim Aufbau der Registry zusammenträgt.
 *
 * @param interfaceName   vollqualifiziertes Service-Interface; ohne {@code interface}-Attribut die id im Paket des Moduls
 * @param implementations create-instance/invoke-factory aus dem service-point und allen {@code <implementation>}-Elementen
 * @param interceptors    vollqualifizierte Service-ids der Interceptor-Factories
 */
public record ServiceInfo(@NotNull Point point, @NotNull String interfaceName,
                          @NotNull List<ServiceImplementation> implementations, @NotNull List<String> interceptors) {

    static @NotNull ServiceInfo create(@NotNull Point point, @NotNull HiveMindRegistry registry) {
        final String declared = HiveMindRegistry.attr(point.tag(), ATTR_INTERFACE);
        final String interfaceName = HiveMindIds.qualifyClass(point.module().packageName(), declared != null ? declared : point.localId());
        final List<String> interceptors = registry.getInterceptors(point.id()).stream()
                .map(tag -> interceptorId(tag, registry))
                .toList();
        return new ServiceInfo(point, interfaceName, registry.getImplementations(point.id()), interceptors);
    }

    private static String interceptorId(XmlTag interceptor, HiveMindRegistry registry) {
        final HiveModule module = registry.getModule(interceptor.getContainingFile());
        final String id = HiveMindRegistry.attr(interceptor, ATTR_SERVICE_ID);
        return id != null ? HiveMindIds.qualify(module, id) : "?";
    }

    public @NotNull String id() {
        return point.id();
    }

    /**
     * Wirksame Implementierung: ein Override aus {@code <implementation>} ersetzt die Standardimplementierung im
     * {@code <service-point>}. Mehr als ein Override ist ein Fehler (siehe {@link #overrides()}); dann gilt der erste.
     */
    public @Nullable ServiceImplementation primaryImplementation() {
        final List<ServiceImplementation> overrides = overrides();
        if (!overrides.isEmpty()) return overrides.getFirst();
        return implementations.isEmpty() ? null : implementations.getFirst();
    }

    public @NotNull List<ServiceImplementation> overrides() {
        return implementations.stream().filter(implementation -> !implementation.isDefault()).toList();
    }

    public @NotNull String model() {
        final ServiceImplementation implementation = primaryImplementation();
        return implementation != null ? implementation.model() : DEFAULT_MODEL;
    }
}
