package com.github.wcqtech.jakit.enumdict.convert;

import com.github.wcqtech.jakit.enumdict.DictItem;
import com.github.wcqtech.jakit.enumdict.EnumDictRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnumDictConverterTest {

    private EnumDictRegistry registry;
    private EnumDictConverter converter;

    @BeforeEach
    void setUp() {
        registry = new EnumDictRegistry();
        registry.register("order_status", List.of(
                new DictItem("order_status", "1", "待支付"),
                new DictItem("order_status", "2", "已支付")));
        registry.register("user_level", List.of(
                new DictItem("user_level", "A", "普通用户"),
                new DictItem("user_level", "B", "VIP")));
        registry.register("person_role", List.of(
                new DictItem("person_role", "father", "父亲"),
                new DictItem("person_role", "child", "儿子")));
        registry.register("node_type", List.of(
                new DictItem("node_type", "root", "根节点"),
                new DictItem("node_type", "child", "子节点")));
        converter = new EnumDictConverter(registry);
    }

    @Test
    void convertsInPlaceWhenKeyDefaultsToAnnotatedField() {
        Order order = new Order();

        converter.convert(order);

        assertEquals("待支付", order.status);
    }

    @Test
    void convertsInPlaceWhenKeyExplicitlyNamesAnnotatedField() {
        ExplicitSelfKey bean = new ExplicitSelfKey();

        converter.convert(bean);

        assertEquals("已支付", bean.status);
    }

    @Test
    void convertsLabelFieldFromSiblingKeyAndPreservesSource() {
        User user = new User();

        converter.convert(user);

        assertEquals("普通用户", user.levelName);
        assertEquals("A", user.level);
    }

    @Test
    void recursesIntoNestedBeansAndContainers() {
        Order order = new Order();
        order.user = new User();
        order.items = List.of(new Item());
        Map<String, Item> itemMap = new LinkedHashMap<>();
        itemMap.put("item-001", new Item());
        order.itemMap = itemMap;

        converter.convert(order);

        assertEquals("待支付", order.status);
        assertEquals("普通用户", order.user.levelName);
        assertEquals("已支付", order.items.get(0).stateName);
        assertEquals("已支付", order.itemMap.get("item-001").stateName);
    }

    @Test
    void convertsCollectionElements() {
        List<Order> orders = List.of(new Order(), new Order());

        converter.convert(orders);

        assertEquals("待支付", orders.get(0).status);
        assertEquals("待支付", orders.get(1).status);
    }

    @Test
    void convertsObjectArrayElements() {
        Order[] orders = {new Order(), new Order()};

        converter.convert(orders);

        assertEquals("待支付", orders[0].status);
        assertEquals("待支付", orders[1].status);
    }

    @Test
    void convertsNestedObjectArrays() {
        Order[][] orders = {{new Order()}, {new Order()}};

        converter.convert(orders);

        assertEquals("待支付", orders[0][0].status);
        assertEquals("待支付", orders[1][0].status);
    }

    @Test
    void convertsArrayFieldsInsideBeans() {
        ArrayHolder holder = new ArrayHolder();
        holder.orders = new Order[]{new Order()};
        holder.itemMatrix = new Item[][]{{new Item()}};

        converter.convert(holder);

        assertEquals("待支付", holder.orders[0].status);
        assertEquals("已支付", holder.itemMatrix[0][0].stateName);
    }

    @Test
    void skipsPrimitiveArrays() {
        PrimitiveArrayHolder holder = new PrimitiveArrayHolder();
        holder.values = new int[]{1, 2, 3};

        converter.convert(holder);

        assertArrayEquals(new int[]{1, 2, 3}, holder.values);
    }

    @Test
    void convertsMapValues() {
        Order order = new Order();
        Map<String, Order> map = new LinkedHashMap<>();
        map.put("order-001", order);

        converter.convert(map);

        assertEquals("待支付", map.get("order-001").status);
    }

    @Test
    void convertsBothMapKeysAndValues() {
        Father father = new Father();
        Child child = new Child();
        Map<Father, Child> map = new LinkedHashMap<>();
        map.put(father, child);

        converter.convert(map);

        assertEquals("父亲", father.role);
        assertEquals("儿子", child.role);
    }

    @Test
    void convertsNestedCollectionsInsideMapValues() {
        Father father = new Father();
        Set<Person> children = Set.of(new Person(), new Person());
        Map<Father, Set<Person>> family = new LinkedHashMap<>();
        family.put(father, children);

        converter.convert(family);

        assertEquals("父亲", father.role);
        for (Person child : children) {
            assertEquals("儿子", child.role);
        }
    }

    @Test
    void convertsRawCollectionsByRuntimeType() {
        RawHolder holder = new RawHolder();
        holder.list.add(new Order());

        converter.convert(holder);

        assertEquals("待支付", ((Order) holder.list.get(0)).status);
    }

    @Test
    void visitorRunsAfterEachElementConversion() {
        List<Order> orders = List.of(new Order(), new Order());
        List<String> labels = new ArrayList<>();

        converter.convert(orders, order -> labels.add(order.status));

        assertEquals(List.of("待支付", "待支付"), labels);
    }

    @Test
    void stopsCyclesWithIdentityVisitedSet() {
        Node root = new Node();
        Node child = new Node();
        root.type = "root";
        child.type = "child";
        root.children.add(child);
        child.parent = root;
        root.parent = root;

        converter.convert(root);

        assertEquals("根节点", root.type);
        assertEquals("子节点", child.type);
    }

    @Test
    void keepsOriginalValueWhenKeyMissingByDefault() {
        Order order = new Order();
        order.status = "999";

        converter.convert(order);

        assertEquals("999", order.status);
    }

    @Test
    void throwsWhenKeyMissingAndPolicyFails() {
        EnumDictConverter failing = new EnumDictConverter(registry, MissingPolicy.FAIL);
        Order order = new Order();
        order.status = "999";

        EnumDictConvertException error = assertThrows(EnumDictConvertException.class, () -> failing.convert(order));

        assertTrue(error.getMessage().contains("order_status"));
        assertTrue(error.getMessage().contains("999"));
    }

    @Test
    void skipsNullKeySource() {
        User user = new User();
        user.level = null;

        converter.convert(user);

        assertNull(user.levelName);
    }

    @Test
    void skipsFinalFields() {
        FinalBean bean = new FinalBean();

        converter.convert(bean);

        assertEquals("1", bean.status);
    }

    @Test
    void skipsRecords() {
        RecordBean bean = new RecordBean("1");

        converter.convert(bean);

        assertEquals("1", bean.status());
    }

    @Test
    void leavesJdkValueTypesUntouched() {
        ValueHolder holder = new ValueHolder();

        converter.convert(holder);

        assertEquals("待支付", holder.status);
        assertEquals(BigDecimal.ONE, holder.amount);
        assertEquals(LocalDate.of(2026, 8, 8), holder.date);
    }

    @Test
    void usesFieldsDeclaredInSuperclass() {
        Derived derived = new Derived();

        converter.convert(derived);

        assertEquals("待支付", derived.status);
    }

    @Test
    void returnsSameInstanceForChaining() {
        Order order = new Order();

        assertSame(order, converter.convert(order));
    }

    @Test
    void rejectsNullArguments() {
        assertThrows(NullPointerException.class, () -> converter.convert((Object) null));
        assertThrows(NullPointerException.class, () -> converter.convert((Collection<Order>) null));
        assertThrows(NullPointerException.class,
                () -> converter.convert(List.of(new Order()), (Consumer<? super Order>) null));
    }

    @Test
    void rejectsNonStringAnnotatedField() {
        NonStringBean bean = new NonStringBean();

        assertThrows(IllegalArgumentException.class, () -> converter.convert(bean));
    }

    @Test
    void rejectsMissingKeyField() {
        MissingKeyBean bean = new MissingKeyBean();

        assertThrows(IllegalArgumentException.class, () -> converter.convert(bean));
    }

    @Test
    void convertsConcurrentlyWithCachedMetadata() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                futures.add(pool.submit(() -> {
                    Order order = new Order();
                    converter.convert(order);
                    assertEquals("待支付", order.status);
                    return null;
                }));
            }
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    static class Order {
        @DictField(type = "order_status")
        String status = "1";
        User user;
        List<Item> items;
        Map<String, Item> itemMap;
    }

    static class ExplicitSelfKey {
        @DictField(type = "order_status", keyField = "status")
        String status = "2";
    }

    static class User {
        @DictField(type = "user_level", keyField = "level")
        String levelName;
        String level = "A";
    }

    static class Item {
        @DictField(type = "order_status", keyField = "state")
        String stateName;
        String state = "2";
    }

    static class Father {
        @DictField(type = "person_role")
        String role = "father";
    }

    static class Child {
        @DictField(type = "person_role")
        String role = "child";
    }

    static class Person {
        @DictField(type = "person_role")
        String role = "child";
    }

    @SuppressWarnings("rawtypes")
    static class RawHolder {
        List list = new ArrayList();
    }

    static class ArrayHolder {
        Order[] orders;
        Item[][] itemMatrix;
    }

    static class PrimitiveArrayHolder {
        int[] values;
    }

    static class Node {
        @DictField(type = "node_type")
        String type;
        Node parent;
        List<Node> children = new ArrayList<>();
    }

    static class FinalBean {
        @DictField(type = "order_status")
        final String status = "1";
    }

    record RecordBean(@DictField(type = "order_status") String status) {
    }

    static class ValueHolder {
        @DictField(type = "order_status")
        String status = "1";
        BigDecimal amount = BigDecimal.ONE;
        LocalDate date = LocalDate.of(2026, 8, 8);
    }

    static class BaseOrder {
        @DictField(type = "order_status")
        String status = "1";
    }

    static class Derived extends BaseOrder {
    }

    static class NonStringBean {
        @DictField(type = "order_status")
        Integer status = 1;
    }

    static class MissingKeyBean {
        @DictField(type = "order_status", keyField = "absent")
        String statusName;
    }
}
