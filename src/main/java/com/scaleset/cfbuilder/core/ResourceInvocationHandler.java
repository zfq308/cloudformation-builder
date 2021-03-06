package com.scaleset.cfbuilder.core;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.scaleset.cfbuilder.annotations.Type;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class ResourceInvocationHandler<T extends Resource> implements InvocationHandler {

    private final static JsonNodeFactory nodeFactory = JsonNodeFactory.instance;

    @JsonIgnore
    private String id;

    private Class<T> resourceClass;

    private T proxy;

    @JsonProperty("Type")
    private String type;

    @JsonProperty("Properties")
    private ObjectNode properties = JsonNodeFactory.instance.objectNode();

    public ResourceInvocationHandler(Class<T> resourceClass, String id) {
        if (resourceClass.isAnnotationPresent(Type.class)) {
            Type type = resourceClass.getAnnotation(Type.class);
            this.type = type.value();
        } else {
            throw new IllegalArgumentException("Type annotation required");
        }
        this.id = id;
        this.resourceClass = resourceClass;
    }

    public ResourceInvocationHandler(Class<T> resourceClass, String type, String id, ObjectNode properties) {
        this.resourceClass = resourceClass;
        this.type = type;
        this.id = id;
        this.properties = properties;
    }

    protected Object doDefaultMethod(Object proxy, Method method, Object[] args) throws Throwable {

        final Constructor<MethodHandles.Lookup> constructor = MethodHandles.Lookup.class.getDeclaredConstructor(Class.class, int.class);
        if (!constructor.isAccessible()) {
            constructor.setAccessible(true);
        }

        Object result = null;
        if (method.isDefault()) {
            final Class<?> declaringClass = method.getDeclaringClass();
            result = constructor.newInstance(declaringClass, MethodHandles.Lookup.PRIVATE)
                    .unreflectSpecial(method, declaringClass)
                    .bindTo(proxy)
                    .invokeWithArguments(args);
        }

        // proxy impl of not defaults methods
        return result;
    }

    protected Object doSetter(Object proxy, Method method, Object[] args) {
        Object result = null;
        String propertyName = getPropertyName(method);

        // We know args.length is 1 from isSetter check method
        Object value = args[0];


        if (isArrayProperty(method, args)) {
            setArrayProperty(propertyName, (Object[]) value);
        } else {
            setProperty(propertyName, value);
        }

        if (method.getReturnType().equals(resourceClass)) {
            result = proxy;
        }
        return result;
    }

    private Tag doTag(Object proxy, Method method, Object[] args) {
        String key = args[0].toString();
        String value = args[1].toString();
        Tag tag = new Tag(key, value);
        //node.withArray("Tags").addObject().put(key, value);
        properties.withArray("Tags").add(tag.toNode());
        return tag;
    }

    /**
     * Get the setProperty name of the variable from the getter/setter method name
     */
    private String getPropertyName(Method method) {
        String result = method.getName();
        if (result.startsWith("set") || result.startsWith("get")) {
            char[] chars = result.substring(3).toCharArray();
            chars[0] = Character.toLowerCase(chars[0]);
            result = String.valueOf(chars);
        }
        result = Character.toUpperCase(result.charAt(0)) + result.substring(1);
        return result;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        Object result = null;

        Class<?> declaringClass = method.getDeclaringClass();

        if (isGetter(method, args)) {
            String name = getPropertyName(method);
            if ("ref".equals(name)) {
                result = ref();
            } else if ("Id".equals(name)) {
                result = id;
            } else if ("Type".equals(name)) {
                result = type;
            } else if ("Properties".equals(name)) {
                return properties;
            }
        } else if (declaringClass.equals(Object.class)) {
            result = method.invoke(this, args);
        } else if (isRef(method, args)) {
            result = ref();
        } else if (isTag(method, args)) {
            result = doTag(proxy, method, args);
        } else if (method.isDefault()) {
            result = doDefaultMethod(proxy, method, args);
        } else if (isSetter(method, args)) {
            result = doSetter(proxy, method, args);
        } else {
            throw new UnsupportedOperationException();
        }
        return result;
    }

    protected boolean isArrayProperty(Method method, Object[] args) {
        boolean result = false;
        if (args.length == 1 && method.getParameters()[0].isVarArgs()) {
            result = true;
        }
        return result;
    }

    private boolean isGetNode(Method method, Object[] args) {
        return "getNode".equals(method.getName());
    }

    private boolean isGetter(Method method, Object[] args) {
        return method.getName().startsWith("get") && (args == null || args.length == 0);
    }

    private boolean isRef(Method method, Object[] args) {
        return "ref".equals(method.getName());
    }

    /**
     * Check whether or not method is a setter with one argument and uses
     * optional default value if null
     */
    private boolean isSetter(Method method, Object[] args) {
        return args != null && args.length == 1;
    }

    private boolean isTag(Method method, Object[] args) {
        return "tag".equals(method.getName()) && args.length == 2;
    }

    public T proxy() {
        if (proxy == null) {
            proxy = (T) Proxy.newProxyInstance(resourceClass.getClassLoader(), new Class[]{resourceClass}, this);
        }
        return proxy;
    }

    private Ref ref() {
        return new Ref(id);
    }

    protected void setArrayProperty(String name, Object[] values) {
        ArrayNode node = properties.withArray(name);

        // Resource, Ref, Property, Function, Parameter, Primitive
        // Primitives: Float, Double,

        for (Object obj : values) {
            JsonNode valueNode = toNode(obj);
            if (!valueNode.isNull()) {
                node.add(valueNode);
            }
        }
    }

    public void setProperty(String name, Object obj) {
        JsonNode valueNode = toNode(obj);
        if (!valueNode.isNull()) {
            properties.put(name, valueNode);
        }
    }

    protected JsonNode toNode(Object obj) {
        JsonNode result;
        if (obj instanceof Ref) {
            result = ((Ref) obj).toNode();
        } else if (obj instanceof Referenceable) {
            result = ((Referenceable) obj).ref().toNode();
        } else {
            result = nodeFactory.pojoNode(obj);
        }
        return result;
    }

    protected Object toPropertyValue(Object value) {
        if (value instanceof Resource) {
            return ((Resource) value).ref();
        } else if (value instanceof Parameter) {
            return ((Parameter) value).ref();
        } else {
            return value;
        }
    }
}
