package com.herreromarcos.idea.hivemindplugin.model;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootModificationTracker;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.herreromarcos.idea.hivemindplugin.HiveMindConstants.*;

/**
 * Projektweites Verzeichnis aller HiveMind-Module mit ihren Service-Points, Configuration-Points, Schemas und
 * Service-Implementierungen (gecacht bis zur nächsten PSI-/Root-Änderung).
 *
 * <p>Gefunden werden alle {@code hivemodule.xml} im Projekt und im Classpath (z.B. {@code hivemind-1.1.jar!/META-INF/hivemodule.xml})
 * sowie rekursiv die per {@code <sub-module descriptor="..."/>} eingebundenen Deskriptoren.
 */
public class HiveMindRegistry {
    private static final Key<CachedValue<HiveMindRegistry>> KEY = Key.create("hivemind.registry");

    private final Project project;
    private final List<HiveModule> modules = new ArrayList<>();
    private final Map<VirtualFile, HiveModule> modulesByFile = new HashMap<>();
    private final Map<PointKind, Map<String, List<Point>>> points = new EnumMap<>(PointKind.class);
    private final Map<String, List<ServiceImplementation>> implementations = new HashMap<>();
    private final Map<String, List<XmlTag>> interceptors = new HashMap<>();
    /** Symbol → {@code <default symbol=... value=...>}, ApplicationDefaults vor FactoryDefaults. */
    private final Map<String, List<XmlTag>> symbolDefaults = new TreeMap<>();
    private Map<String, ServiceInfo> services;
    private Map<String, List<ServiceInfo>> servicesByClass;

    private HiveMindRegistry(Project project) {
        this.project = project;
        for (final PointKind kind : PointKind.values()) points.put(kind, new LinkedHashMap<>());
    }

    public static @NotNull HiveMindRegistry getInstance(@NotNull Project project) {
        return CachedValuesManager.getManager(project).getCachedValue(project, KEY, () ->
                CachedValueProvider.Result.create(compute(project),
                        PsiModificationTracker.MODIFICATION_COUNT, ProjectRootModificationTracker.getInstance(project)), false);
    }

    // ------------------------------------------------------------------ Abfragen

    public @NotNull List<HiveModule> getModules() {
        return Collections.unmodifiableList(modules);
    }

    public @NotNull List<HiveModule> findModules(@NotNull String id) {
        return modules.stream().filter(module -> module.id().equals(id.trim())).toList();
    }

    /** Modul, dessen Deskriptor die Datei ist, sonst {@code null}. */
    public @Nullable HiveModule getModule(@Nullable PsiFile file) {
        final VirtualFile vf = file != null ? file.getOriginalFile().getVirtualFile() : null;
        return vf != null ? modulesByFile.get(vf) : null;
    }

    /** Ist die Datei ein HiveMind-Moduldeskriptor (hivemodule.xml oder eingebundenes Sub-Modul)? */
    public static boolean isDescriptor(@Nullable PsiFile file) {
        if (!(file instanceof final XmlFile xml)) return false;
        final XmlTag root = xml.getRootTag();
        if (root == null || !TAG_MODULE.equals(root.getName())) return false;
        if (DESCRIPTOR_FILE_NAME.equals(file.getName())) return true;
        return getInstance(file.getProject()).getModule(file) != null;
    }

    /** Modul des Deskriptors, in dem das Element steht. */
    public static @Nullable HiveModule moduleOf(@NotNull PsiElement element) {
        final PsiFile file = element.getContainingFile();
        return file != null ? getInstance(element.getProject()).getModule(file) : null;
    }

    public @NotNull Collection<Point> getPoints(@NotNull PointKind kind) {
        return points.get(kind).values().stream().flatMap(List::stream).toList();
    }

    /** Alle Punkte mit der (vollqualifizierten) id – mehr als einer bedeutet einen Konflikt. */
    public @NotNull List<Point> findPoints(@NotNull PointKind kind, @NotNull String qualifiedId) {
        return points.get(kind).getOrDefault(qualifiedId.trim(), List.of());
    }

    public @NotNull List<ServiceImplementation> getImplementations(@NotNull String serviceId) {
        return implementations.getOrDefault(serviceId, List.of());
    }

    public @NotNull List<XmlTag> getInterceptors(@NotNull String serviceId) {
        return interceptors.getOrDefault(serviceId, List.of());
    }

    /**
     * Standardwerte eines Substitutionssymbols (Doku "Configurations", "hivemind.FactoryDefaults",
     * "hivemind.ApplicationDefaults"): {@code <default symbol="..." value="..."/>}; die Anwendungswerte zuerst, weil sie
     * die Factory-Werte überschreiben.
     */
    public @NotNull List<XmlTag> findSymbolDefaults(@NotNull String symbol) {
        return symbolDefaults.getOrDefault(symbol.trim(), List.of());
    }

    public @NotNull Set<String> getSymbolNames() {
        return Collections.unmodifiableSet(symbolDefaults.keySet());
    }

    /** Alle Services mit Interface und Implementierungen, sortiert nach id. */
    public synchronized @NotNull Collection<ServiceInfo> getServices() {
        if (services == null) {
            final Map<String, ServiceInfo> result = new TreeMap<>();
            for (final Point point : getPoints(PointKind.SERVICE)) {
                result.putIfAbsent(point.id(), ServiceInfo.create(point, this));
            }
            services = result;
        }
        return services.values();
    }

    /** Service mit der (vollqualifizierten) id oder {@code null}. */
    public @Nullable ServiceInfo findService(@NotNull String serviceId) {
        getServices();
        return services.get(serviceId);
    }

    /** Services, deren Interface oder Implementierung die Klasse ist. */
    public synchronized @NotNull List<ServiceInfo> findServicesByClass(@NotNull String qualifiedClassName) {
        if (servicesByClass == null) {
            final Map<String, List<ServiceInfo>> result = new HashMap<>();
            for (final ServiceInfo service : getServices()) {
                final Set<String> classes = new LinkedHashSet<>();
                classes.add(service.interfaceName());
                for (final ServiceImplementation implementation : service.implementations()) {
                    if (implementation.className() != null) classes.add(implementation.className());
                }
                classes.forEach(name -> result.computeIfAbsent(name, k -> new ArrayList<>()).add(service));
            }
            servicesByClass = result;
        }
        return servicesByClass.getOrDefault(qualifiedClassName, List.of());
    }

    // ------------------------------------------------------------------ Aufbau

    private static HiveMindRegistry compute(Project project) {
        final HiveMindRegistry registry = new HiveMindRegistry(project);
        final ProjectFileIndex fileIndex = ProjectFileIndex.getInstance(project);
        final Deque<VirtualFile> queue = new ArrayDeque<>(
                FilenameIndex.getVirtualFilesByName(DESCRIPTOR_FILE_NAME, GlobalSearchScope.allScope(project)));
        final Set<VirtualFile> visited = new HashSet<>();
        while (!queue.isEmpty()) {
            final VirtualFile vf = queue.poll();
            if (!vf.isValid() || !visited.add(vf)) continue;
            if (!(PsiManager.getInstance(project).findFile(vf) instanceof final XmlFile xml)) continue;
            final XmlTag root = xml.getRootTag();
            final String id = root != null && TAG_MODULE.equals(root.getName()) ? attr(root, ATTR_ID) : null;
            if (id == null) continue;
            // ohne package-Attribut ist das Paket die Modul-id
            final String packageName = attr(root, ATTR_PACKAGE);
            final HiveModule module = new HiveModule(id, packageName != null ? packageName : id, xml, root, fileIndex.isInContent(vf));
            registry.add(module);
            for (final XmlTag subModule : root.findSubTags(TAG_SUB_MODULE)) {
                final VirtualFile target = resolveSubModule(subModule, vf);
                if (target != null) queue.add(target);
            }
        }
        return registry;
    }

    /** {@code <sub-module descriptor="..."/>}: Pfad relativ zum einbindenden Deskriptor. */
    public static @Nullable VirtualFile resolveSubModule(@NotNull XmlTag subModule, @Nullable VirtualFile descriptor) {
        final String path = attr(subModule, ATTR_DESCRIPTOR);
        final VirtualFile dir = descriptor != null ? descriptor.getParent() : null;
        return path != null && dir != null ? dir.findFileByRelativePath(path) : null;
    }

    private void add(HiveModule module) {
        modules.add(module);
        modulesByFile.put(module.file().getVirtualFile(), module);
        for (final XmlTag tag : module.root().getSubTags()) {
            switch (tag.getName()) {
                case TAG_SERVICE_POINT -> {
                    final String id = addPoint(PointKind.SERVICE, module, tag);
                    if (id != null) addConstructions(id, module, tag);
                }
                case TAG_CONFIGURATION_POINT -> addPoint(PointKind.CONFIGURATION, module, tag);
                case TAG_SCHEMA -> addPoint(PointKind.SCHEMA, module, tag);
                case TAG_CONTRIBUTION -> addSymbolDefaults(module, tag);
                case TAG_IMPLEMENTATION -> {
                    final String serviceId = attr(tag, ATTR_SERVICE_ID);
                    if (serviceId != null) addConstructions(HiveMindIds.qualify(module.id(), serviceId), module, tag);
                }
                default -> {
                }
            }
        }
    }

    private @Nullable String addPoint(PointKind kind, HiveModule module, XmlTag tag) {
        final String id = attr(tag, ATTR_ID);
        if (id == null) return null;
        final String qualified = module.pointId(id);
        points.get(kind).computeIfAbsent(qualified, k -> new ArrayList<>()).add(new Point(kind, qualified, module, tag));
        return qualified;
    }

    private void addConstructions(String serviceId, HiveModule module, XmlTag parent) {
        for (final XmlTag child : parent.getSubTags()) {
            final ServiceImplementation implementation = switch (child.getName()) {
                case TAG_CREATE_INSTANCE -> {
                    final String className = attr(child, ATTR_CLASS);
                    yield new ServiceImplementation(child, module, className != null ? qualifiedClassName(module, className) : null,
                            null, model(child));
                }
                case TAG_INVOKE_FACTORY -> {
                    final String factory = factoryId(child, module);
                    // nur die BuilderFactory erzeugt <construct class="...">; andere Factories haben eigene Parameter
                    final XmlTag construct = DEFAULT_FACTORY.equals(factory) ? child.findFirstSubTag(TAG_CONSTRUCT) : null;
                    final String className = construct != null ? attr(construct, ATTR_CLASS) : null;
                    yield new ServiceImplementation(child, module, className != null ? qualifiedClassName(module, className) : null,
                            factory, model(child));
                }
                default -> null;
            };
            if (implementation != null) implementations.computeIfAbsent(serviceId, k -> new ArrayList<>()).add(implementation);
            if (TAG_INTERCEPTOR.equals(child.getName())) interceptors.computeIfAbsent(serviceId, k -> new ArrayList<>()).add(child);
        }
    }

    private void addSymbolDefaults(HiveModule module, XmlTag contribution) {
        final String id = attr(contribution, ATTR_CONFIGURATION_ID);
        final String qualified = id != null ? HiveMindIds.qualify(module.id(), id) : null;
        final boolean application = APPLICATION_DEFAULTS.equals(qualified);
        if (!application && !FACTORY_DEFAULTS.equals(qualified)) return;
        for (final XmlTag defaultTag : contribution.findSubTags(TAG_DEFAULT)) {
            final String symbol = attr(defaultTag, ATTR_SYMBOL);
            if (symbol == null) continue;
            final List<XmlTag> list = symbolDefaults.computeIfAbsent(symbol, k -> new ArrayList<>());
            if (application) list.addFirst(defaultTag);
            else list.add(defaultTag);
        }
    }

    private String qualifiedClassName(HiveModule module, String className) {
        return HiveMindClasses.qualifiedName(project, module.packageName(), className);
    }

    /** Factory eines {@code <invoke-factory>}: {@code service-id} (relativ zum Modul), Standard {@code hivemind.BuilderFactory}. */
    public static @NotNull String factoryId(@NotNull XmlTag invokeFactory, @Nullable HiveModule module) {
        final String factory = attr(invokeFactory, ATTR_SERVICE_ID);
        return factory != null ? HiveMindIds.qualify(module, factory) : DEFAULT_FACTORY;
    }

    private static String model(XmlTag construction) {
        final String model = attr(construction, ATTR_MODEL);
        return model != null ? model : DEFAULT_MODEL;
    }

    /** Getrimmter Attributwert oder {@code null}, wenn das Attribut fehlt oder leer ist. */
    public static @Nullable String attr(@NotNull XmlTag tag, @NotNull String name) {
        final String value = tag.getAttributeValue(name);
        return value == null || value.isBlank() ? null : value.trim();
    }
}
