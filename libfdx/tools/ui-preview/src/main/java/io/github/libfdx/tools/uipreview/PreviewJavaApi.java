package io.github.libfdx.tools.uipreview;

import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.util.ArrayList;
import java.util.List;

/** Conservative dependency boundary: changed visible declarations/constants require Gradle recompilation. */
final class PreviewJavaApi {
    static List<String> signature(ClassModel model) {
        List<String> members = new ArrayList<>();
        members.add("type:" + model.flags().flagsMask() + ":"
                + model.superclass().map(c -> c.asInternalName()).orElse("") + ":"
                + model.interfaces().stream().map(c -> c.asInternalName()).toList());
        members.add("generic:" + model.findAttribute(Attributes.signature()).map(a -> a.signature().stringValue()));
        for (var field : model.fields()) {
            if ((field.flags().flagsMask() & ClassFile.ACC_PRIVATE) != 0) continue;
            members.add("field:" + field.fieldName().stringValue() + ":" + field.fieldType().stringValue()
                    + ":" + field.flags().flagsMask()
                    + ":" + field.findAttribute(Attributes.signature()).map(a -> a.signature().stringValue())
                    + ":" + field.findAttribute(Attributes.constantValue()).map(a -> a.constant().constantValue()));
        }
        for (var method : model.methods()) {
            if ((method.flags().flagsMask() & ClassFile.ACC_PRIVATE) != 0 || method.methodName().equalsString("<clinit>")) continue;
            members.add("method:" + method.methodName().stringValue() + ":" + method.methodType().stringValue()
                    + ":" + method.flags().flagsMask()
                    + ":" + method.findAttribute(Attributes.signature()).map(a -> a.signature().stringValue())
                    + ":" + method.findAttribute(Attributes.exceptions()).map(a -> a.exceptions().stream()
                            .map(c -> c.asInternalName()).toList()));
        }
        members.sort(String::compareTo);
        return List.copyOf(members);
    }

}
