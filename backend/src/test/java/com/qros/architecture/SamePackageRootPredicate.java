package com.qros.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;

final class SamePackageRootPredicate extends DescribedPredicate<JavaClass> {
    private static final int MODULE_SEGMENT = 2;

    private final String moduleRoot;

    SamePackageRootPredicate(String moduleRoot) {
        super("nằm trong module com.qros.%s".formatted(moduleRoot));
        this.moduleRoot = moduleRoot;
    }

    @Override
    public boolean test(JavaClass javaClass) {
        return moduleRoot.equals(packageRoot(javaClass.getPackageName()));
    }

    static String packageRoot(String packageName) {
        String[] segments = packageName.split("\\.");
        return segments.length > MODULE_SEGMENT ? segments[MODULE_SEGMENT] : "";
    }
}
