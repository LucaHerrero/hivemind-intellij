package com.herreromarcos.idea.hivemindplugin;

/** Element- und Attributnamen der HiveMind-1.1-Moduldeskriptoren (hivemodule.xml). */
public class HiveMindConstants {
    public static final String DESCRIPTOR_FILE_NAME = "hivemodule.xml";

    public static final String TAG_MODULE = "module";
    public static final String TAG_SERVICE_POINT = "service-point";
    public static final String TAG_CONFIGURATION_POINT = "configuration-point";
    public static final String TAG_SCHEMA = "schema";
    public static final String TAG_IMPLEMENTATION = "implementation";
    public static final String TAG_CONTRIBUTION = "contribution";
    public static final String TAG_SUB_MODULE = "sub-module";
    public static final String TAG_CREATE_INSTANCE = "create-instance";
    public static final String TAG_INVOKE_FACTORY = "invoke-factory";
    public static final String TAG_INTERCEPTOR = "interceptor";
    public static final String TAG_CONSTRUCT = "construct";
    public static final String TAG_SERVICE = "service";
    public static final String TAG_CONFIGURATION = "configuration";
    public static final String TAG_OBJECT = "object";
    public static final String TAG_RESOURCE = "resource";
    public static final String TAG_PARAMETERS_SCHEMA = "parameters-schema";
    public static final String TAG_ELEMENT = "element";
    public static final String TAG_ATTRIBUTE = "attribute";
    public static final String TAG_DEFAULT = "default";

    public static final String ATTR_ID = "id";
    public static final String ATTR_PACKAGE = "package";
    /**
     * Projektspezifische Erweiterung (nicht Teil von HiveMind 1.1): Klasse, neben der HiveMind die Klassen des Moduls
     * erzeugt – sie liefert Paket und Classloader. Optional; muss eine Klasse sein, kein Interface.
     */
    public static final String ATTR_NEIGHBOR_CLASS = "neighbor-class";
    public static final String ATTR_INTERFACE = "interface";
    public static final String ATTR_CLASS = "class";
    public static final String ATTR_MODEL = "model";
    public static final String ATTR_VISIBILITY = "visibility";
    public static final String ATTR_SERVICE_ID = "service-id";
    public static final String ATTR_CONFIGURATION_ID = "configuration-id";
    public static final String ATTR_SCHEMA_ID = "schema-id";
    public static final String ATTR_PARAMETERS_SCHEMA_ID = "parameters-schema-id";
    public static final String ATTR_DESCRIPTOR = "descriptor";
    public static final String ATTR_VALUE = "value";
    public static final String ATTR_OBJECT = "object";
    public static final String ATTR_TYPE = "type";
    public static final String ATTR_NAME = "name";
    public static final String ATTR_IF = "if";
    public static final String ATTR_SYMBOL = "symbol";
    public static final String ATTR_TRANSLATOR = "translator";
    public static final String ATTR_CONTENT_TRANSLATOR = "content-translator";

    public static final String VISIBILITY_PRIVATE = "private";
    public static final String DEFAULT_MODEL = "singleton";
    public static final String DEFAULT_FACTORY = "hivemind.BuilderFactory";
    public static final String FACTORY_DEFAULTS = "hivemind.FactoryDefaults";
    public static final String APPLICATION_DEFAULTS = "hivemind.ApplicationDefaults";

    /** Präfixe der HiveMind-Object-Provider, z.B. {@code service:app.Mailer}. */
    public static final String PROVIDER_SERVICE = "service";
    public static final String PROVIDER_CONFIGURATION = "configuration";
    public static final String PROVIDER_INSTANCE = "instance";
    public static final String PROVIDER_SERVICE_PROPERTY = "service-property";
    public static final String PROVIDER_BEAN = "bean";
    public static final String PROVIDER_CLASS = "class";

    /** Tapestry-4-Integration: {@code @InjectObject("service:...")} und {@code <inject object="service:..."/>}. */
    public static final String TAPESTRY_INJECT_OBJECT = "org.apache.tapestry.annotations.InjectObject";
    public static final String TAPESTRY_INJECT = "inject";
    /** Tapestry-Binding-Präfix, z.B. {@code value="hivemind:service:app.Mailer"}. */
    public static final String HIVEMIND_BINDING_PREFIX = "hivemind:";

    /** Java-API: {@code registry.getService("app.Mailer", Mailer.class)}. */
    public static final String REGISTRY_CLASS = "org.apache.hivemind.Registry";
    public static final String MODULE_CLASS = "org.apache.hivemind.internal.Module";

    private HiveMindConstants() {
    }
}
