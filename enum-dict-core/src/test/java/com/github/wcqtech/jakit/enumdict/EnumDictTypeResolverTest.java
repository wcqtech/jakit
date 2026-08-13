package com.github.wcqtech.jakit.enumdict;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnumDictTypeResolverTest {

    @Test
    void resolvesDefaultTypeFromEnumDictSource() {
        assertEquals("DefaultTypeSource", EnumDictTypeResolver.resolve(DefaultTypeSource.class));
    }

    @Test
    void resolvesCustomTypeFromEnumDictSource() {
        assertEquals("custom_source", EnumDictTypeResolver.resolve(CustomTypeSource.class));
    }

    @Test
    void resolvesTypeFromEnumDictAnnotation() {
        assertEquals("annotation_source", EnumDictTypeResolver.resolve(AnnotatedSource.class));
    }

    @Test
    void resolvesDefaultTypeFromEnumDictAnnotation() {
        assertEquals("AnnotatedDefault", EnumDictTypeResolver.resolve(AnnotatedDefault.class));
    }

    @Test
    void prefersEnumDictSourceOverEnumDictAnnotation() {
        assertEquals("interface_source", EnumDictTypeResolver.resolve(InterfaceAndAnnotationSource.class));
    }

    @Test
    void rejectsNonEnumClass() {
        EnumDictException error = assertThrows(EnumDictException.class,
                () -> EnumDictTypeResolver.resolve(NotAnEnum.class));

        assertTrue(error.getMessage().contains("not an enum"));
    }

    @Test
    void rejectsEnumWithoutDictionaryContract() {
        EnumDictException error = assertThrows(EnumDictException.class,
                () -> EnumDictTypeResolver.resolve(PlainEnum.class));

        assertTrue(error.getMessage().contains("neither implements"));
    }

    @Test
    void rejectsEmptyEnumDictSource() {
        EnumDictException error = assertThrows(EnumDictException.class,
                () -> EnumDictTypeResolver.resolve(EmptySource.class));

        assertTrue(error.getMessage().contains("no constants"));
    }

    @Test
    void rejectsInconsistentInterfaceTypes() {
        EnumDictException error = assertThrows(EnumDictException.class,
                () -> EnumDictTypeResolver.resolve(InconsistentSource.class));

        assertTrue(error.getMessage().contains("Inconsistent dictionary type"));
    }

    @Test
    void rejectsBlankInterfaceType() {
        EnumDictException error = assertThrows(EnumDictException.class,
                () -> EnumDictTypeResolver.resolve(BlankTypeSource.class));

        assertTrue(error.getMessage().contains("Blank dictionary type"));
    }

    enum DefaultTypeSource implements EnumDictSource {
        A;

        @Override
        public Object getDictKey() {
            return name();
        }

        @Override
        public Object getDictValue() {
            return name();
        }
    }

    enum CustomTypeSource implements EnumDictSource {
        A;

        @Override
        public String getDictType() {
            return "custom_source";
        }

        @Override
        public Object getDictKey() {
            return name();
        }

        @Override
        public Object getDictValue() {
            return name();
        }
    }

    @EnumDict(type = "annotation_source")
    enum AnnotatedSource {
        A
    }

    @EnumDict
    enum AnnotatedDefault {
        A
    }

    @EnumDict(type = "annotation_source")
    enum InterfaceAndAnnotationSource implements EnumDictSource {
        A;

        @Override
        public String getDictType() {
            return "interface_source";
        }

        @Override
        public Object getDictKey() {
            return name();
        }

        @Override
        public Object getDictValue() {
            return name();
        }
    }

    static class NotAnEnum {
    }

    enum PlainEnum {
        A
    }

    @EnumDict
    enum EmptySource {
    }

    enum InconsistentSource implements EnumDictSource {
        A {
            @Override
            public String getDictType() {
                return "one";
            }
        },
        B {
            @Override
            public String getDictType() {
                return "two";
            }
        };

        @Override
        public Object getDictKey() {
            return name();
        }

        @Override
        public Object getDictValue() {
            return name();
        }
    }

    enum BlankTypeSource implements EnumDictSource {
        A;

        @Override
        public String getDictType() {
            return " ";
        }

        @Override
        public Object getDictKey() {
            return name();
        }

        @Override
        public Object getDictValue() {
            return name();
        }
    }
}
