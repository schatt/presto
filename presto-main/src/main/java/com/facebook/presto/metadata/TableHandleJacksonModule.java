package com.facebook.presto.metadata;

import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.jsontype.TypeDeserializer;
import com.fasterxml.jackson.databind.jsontype.TypeIdResolver;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import com.fasterxml.jackson.databind.jsontype.impl.AsPropertyTypeDeserializer;
import com.fasterxml.jackson.databind.jsontype.impl.AsPropertyTypeSerializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanSerializerFactory;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.fasterxml.jackson.databind.type.SimpleType;
import com.google.common.base.Throwables;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;

import javax.inject.Inject;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

public class TableHandleJacksonModule
        extends SimpleModule
{
    @Inject
    public TableHandleJacksonModule(Map<String, Class<? extends TableHandle>> tableHandleTypes)
    {
        super(TableHandleJacksonModule.class.getSimpleName(), Version.unknownVersion());

        TypeIdResolver typeResolver = new TableHandleTypeResolver(tableHandleTypes);

        addSerializer(TableHandle.class, new TableHandleSerializer(typeResolver));
        addDeserializer(TableHandle.class, new TableHandleDeserializer(typeResolver));
    }

    public static class TableHandleDeserializer
            extends StdDeserializer<TableHandle>
    {
        private final TypeDeserializer typeDeserializer;

        public TableHandleDeserializer(TypeIdResolver typeIdResolver)
        {
            super(TableHandle.class);
            this.typeDeserializer = new AsPropertyTypeDeserializer(SimpleType.construct(TableHandle.class), typeIdResolver, "type", false, null);
        }

        @Override
        public TableHandle deserialize(JsonParser jsonParser, DeserializationContext deserializationContext)
                throws IOException, JsonProcessingException
        {
            return (TableHandle) typeDeserializer.deserializeTypedFromAny(jsonParser, deserializationContext);
        }
    }

    public static class TableHandleSerializer
            extends StdSerializer<TableHandle>
    {
        private final TypeSerializer typeSerializer;
        private final Cache<Class<? extends TableHandle>, JsonSerializer<Object>> serializerCache = CacheBuilder.newBuilder().build();

        public TableHandleSerializer(TypeIdResolver typeIdResolver)
        {
            super(TableHandle.class);
            this.typeSerializer = new AsPropertyTypeSerializer(typeIdResolver, null, "type");
        }

        @Override
        public void serialize(final TableHandle value, JsonGenerator jsonGenerator, final SerializerProvider serializerProvider)
                throws IOException, JsonGenerationException
        {
            if (value == null) {
                serializerProvider.defaultSerializeNull(jsonGenerator);
            }
            else {
                try {
                    JsonSerializer<Object> serializer = serializerCache.get(value.getClass(), new Callable<JsonSerializer<Object>>() {

                        @Override
                        public JsonSerializer<Object> call()
                                throws Exception
                        {
                            return BeanSerializerFactory.instance.createSerializer(serializerProvider, serializerProvider.constructType(value.getClass()));
                        }

                    });

                    serializer.serializeWithType(value, jsonGenerator, serializerProvider, typeSerializer);
                }
                catch (ExecutionException e) {
                    Throwables.propagateIfInstanceOf(e.getCause(), IOException.class);
                    Throwables.propagateIfInstanceOf(e.getCause(), JsonGenerationException.class);
                    throw Throwables.propagate(e.getCause());
                }
            }
        }
    }

    public static class TableHandleTypeResolver
            implements TypeIdResolver
    {
        private final BiMap<String, Class<? extends TableHandle>> tableHandleTypes;
        private final Map<Class<? extends TableHandle>, SimpleType> simpleTypes;

        public TableHandleTypeResolver(Map<String, Class<? extends TableHandle>> tableHandleTypes)
        {
            this.tableHandleTypes = ImmutableBiMap.copyOf(tableHandleTypes);

            ImmutableMap.Builder<Class<? extends TableHandle>, SimpleType> builder = ImmutableMap.builder();
            for (Class<? extends TableHandle> handleClass : this.tableHandleTypes.values()) {
                builder.put(handleClass, SimpleType.construct(handleClass));
            }
            this.simpleTypes = builder.build();
        }

        @Override
        public void init(JavaType baseType)
        {
        }

        @Override
        public String idFromValue(Object value)
        {
            checkNotNull(value, "value was null!");
            return idFromValueAndType(value, value.getClass());
        }

        @Override
        public String idFromValueAndType(Object value, Class<?> suggestedType)
        {
            String type = tableHandleTypes.inverse().get(suggestedType);
            checkState(type != null, "Class %s is unknown!", suggestedType.getSimpleName());
            return type;
        }

        @Override
        public String idFromBaseType()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public JavaType typeFromId(String id)
        {
            Class<?> tableHandleClass = tableHandleTypes.get(id);
            checkState(tableHandleClass != null, "Type %s is unknown!", id);
            return simpleTypes.get(tableHandleClass);
        }

        @Override
        public Id getMechanism()
        {
            return Id.NAME;
        }
    }
}