package com.github.wcqtech.jakit.utils.mock;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.github.wcqtech.jakit.utils.mock.mocker.BooleanMocker;
import com.github.wcqtech.jakit.utils.mock.mocker.IntegerMocker;

class MockersTest {

    @Test
    void builtInRulesResolveCommonTypes() {
        assertNotNull(Mockers.find(String.class));
        assertNotNull(Mockers.find(Integer.class));
        assertNotNull(Mockers.find(Long.class));
        assertNotNull(Mockers.find(Boolean.class));
        assertNotNull(Mockers.find(Character.class));
        assertNotNull(Mockers.find(Float.class));
        assertNotNull(Mockers.find(Double.class));
        assertNotNull(Mockers.find(BigDecimal.class));
        assertNotNull(Mockers.find(LocalDateTime.class));
    }

    @Test
    void primitiveTypesResolveThroughTheirWrapper() {
        assertInstanceOf(IntegerMocker.class, Mockers.find(int.class));
        assertInstanceOf(BooleanMocker.class, Mockers.find(boolean.class));
        assertNotNull(Mockers.find(long.class));
        assertNotNull(Mockers.find(short.class));
        assertNotNull(Mockers.find(byte.class));
        assertNotNull(Mockers.find(char.class));
        assertNotNull(Mockers.find(float.class));
        assertNotNull(Mockers.find(double.class));
    }

    @Test
    void unresolvedTypeReturnsNull() {
        assertNull(Mockers.find(UUID.class));
        assertNull(Mockers.find(StringBuilder.class));
    }

    @Test
    void laterRegistrationWins() {
        Mocker<Sample> first = context -> new Sample();
        Mocker<Sample> second = context -> new Sample();
        Mockers.register(Sample.class, first);
        Mockers.register(Sample.class, second);
        Mocker<?> found = Mockers.find(Sample.class);
        assertNotNull(found);
        assertSame(second, found);
    }

    @Test
    void registeredSupertypeCoversSubtype() {
        Mocker<Animal> animal = context -> new Dog();
        Mockers.register(Animal.class, animal);
        Mocker<?> found = Mockers.find(Dog.class);
        assertNotNull(found);
        assertSame(animal, found);
    }

    @Test
    void registrationExtendsBeyondBuiltIns() {
        Mocker<Extension> extensionMocker = context -> new Extension();
        Mockers.register(Extension.class, extensionMocker);
        Mocker<?> found = Mockers.find(Extension.class);
        assertNotNull(found);
        assertSame(extensionMocker, found);
    }

    @Test
    void nullArgumentsAreRejected() {
        assertThrows(NullPointerException.class, () -> Mockers.register(null, context -> new Sample()));
        assertThrows(NullPointerException.class, () -> Mockers.register(Sample.class, null));
        assertThrows(NullPointerException.class, () -> Mockers.find(null));
    }

    private static class Sample {
    }

    private static class Animal {
    }

    private static class Dog extends Animal {
    }

    private static class Extension {
    }
}