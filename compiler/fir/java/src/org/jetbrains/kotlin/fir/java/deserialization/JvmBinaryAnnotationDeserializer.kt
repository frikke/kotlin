/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.java.deserialization

import org.jetbrains.kotlin.descriptors.SourceElement
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.deserialization.AbstractAnnotationDeserializer
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.expressions.builder.buildAnnotation
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.load.kotlin.*
import org.jetbrains.kotlin.metadata.ProtoBuf
import org.jetbrains.kotlin.metadata.deserialization.*
import org.jetbrains.kotlin.metadata.jvm.JvmProtoBuf
import org.jetbrains.kotlin.metadata.jvm.deserialization.JvmFlags
import org.jetbrains.kotlin.metadata.jvm.deserialization.JvmProtoBufUtil
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.protobuf.MessageLite
import org.jetbrains.kotlin.resolve.constants.ClassLiteralValue
import org.jetbrains.kotlin.serialization.deserialization.builtins.BuiltInSerializerProtocol
import org.jetbrains.kotlin.serialization.deserialization.descriptors.DeserializedContainerSource
import org.jetbrains.kotlin.utils.ReusableByteArray
import org.jetbrains.kotlin.utils.addToStdlib.runIf


fun JvmBinaryAnnotationDeserializer(
    session: FirSession,
    kotlinBinaryClass: KotlinJvmBinaryClass,
    kotlinClassFinder: KotlinClassFinder,
    byteContent: ReusableByteArray?
) = JvmBinaryAnnotationDeserializer(
    session,
    kotlinBinaryClass,
    kotlinClassFinder,
    readMemberAnnotationNodes(kotlinBinaryClass, byteContent)
)

class JvmBinaryAnnotationDeserializer(
    val session: FirSession,
    kotlinBinaryClass: KotlinJvmBinaryClass,
    kotlinClassFinder: KotlinClassFinder,
    memberAnnotationNodes: Map<MemberSignature, List<JvmAnnotationNode>>
) : AbstractAnnotationDeserializer(session, BuiltInSerializerProtocol) {
    private val annotationInfo: MemberAnnotations by lazy(LazyThreadSafetyMode.PUBLICATION) {
        session.loadMemberAnnotations(memberAnnotationNodes, kotlinClassFinder)
    }

    private val annotationInfoForDefaultImpls by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val defaultImplsClassId = kotlinBinaryClass.classId.createNestedClassId(Name.identifier(JvmAbi.DEFAULT_IMPLS_CLASS_NAME))
        val (_, defaultImplsByteContent) = kotlinClassFinder.findKotlinClassOrContent(defaultImplsClassId) as? KotlinClassFinder.Result.KotlinClass
            ?: return@lazy null
        session.loadMemberAnnotations(readMemberAnnotationNodes(kotlinBinaryClass, defaultImplsByteContent), kotlinClassFinder)
    }

    override fun inheritAnnotationInfo(parent: AbstractAnnotationDeserializer) {
        if (parent is JvmBinaryAnnotationDeserializer) {
            annotationInfo.memberAnnotations.putAll(parent.annotationInfo.memberAnnotations)
        }
    }

    override fun loadTypeAnnotations(typeProto: ProtoBuf.Type, nameResolver: NameResolver): List<FirAnnotation> {
        val annotations = typeProto.getExtension(JvmProtoBuf.typeAnnotation).orEmpty()
        return annotations.map { deserializeAnnotation(it, nameResolver) }
    }

    override fun loadTypeParameterAnnotations(typeParameterProto: ProtoBuf.TypeParameter, nameResolver: NameResolver): List<FirAnnotation> {
        val annotations = typeParameterProto.getExtension(JvmProtoBuf.typeParameterAnnotation).orEmpty()
        return annotations.map { deserializeAnnotation(it, nameResolver) }
    }

    override fun loadConstructorAnnotations(
        containerSource: DeserializedContainerSource?,
        constructorProto: ProtoBuf.Constructor,
        nameResolver: NameResolver,
        typeTable: TypeTable
    ): List<FirAnnotation> {
        val signature = getCallableSignature(constructorProto, nameResolver, typeTable) ?: return emptyList()
        return findJvmBinaryClassAndLoadMemberAnnotations(signature)
    }

    override fun loadFunctionAnnotations(
        containerSource: DeserializedContainerSource?,
        functionProto: ProtoBuf.Function,
        nameResolver: NameResolver,
        typeTable: TypeTable
    ): List<FirAnnotation> {
        val signature = getCallableSignature(functionProto, nameResolver, typeTable) ?: return emptyList()
        return findJvmBinaryClassAndLoadMemberAnnotations(signature)
    }

    override fun loadPropertyAnnotations(
        containerSource: DeserializedContainerSource?,
        propertyProto: ProtoBuf.Property,
        containingClassProto: ProtoBuf.Class?,
        nameResolver: NameResolver,
        typeTable: TypeTable
    ): List<FirAnnotation> {
        val signature = getPropertySignature(propertyProto, nameResolver, typeTable, synthetic = true) ?: return emptyList()
        val classIsInterface = containingClassProto?.let { Flags.CLASS_KIND.get(it.flags) == ProtoBuf.Class.Kind.INTERFACE } ?: false
        val jvmClassFlags = runIf(containingClassProto?.hasExtension(JvmProtoBuf.jvmClassFlags) == true) {
            containingClassProto?.getExtension(JvmProtoBuf.jvmClassFlags)
        }
        val allCompatibilityModeIsEnabled = jvmClassFlags?.let { JvmFlags.IS_COMPILED_IN_COMPATIBILITY_MODE.get(it) } ?: true
        return findJvmBinaryClassAndLoadMemberAnnotations(
            signature,
            searchInDefaultImpls = classIsInterface && allCompatibilityModeIsEnabled
        ).map {
            buildAnnotation {
                annotationTypeRef = it.annotationTypeRef
                argumentMapping = it.argumentMapping
                useSiteTarget = AnnotationUseSiteTarget.PROPERTY
            }
        }
    }

    private val MemberSignature.isDelegated: Boolean
        get() = JvmAbi.DELEGATED_PROPERTY_NAME_SUFFIX in this.signature

    override fun loadPropertyBackingFieldAnnotations(
        containerSource: DeserializedContainerSource?,
        propertyProto: ProtoBuf.Property,
        nameResolver: NameResolver,
        typeTable: TypeTable
    ): List<FirAnnotation> {
        val signature = getPropertySignature(propertyProto, nameResolver, typeTable, field = true) ?: return emptyList()
        if (signature.isDelegated) {
            return emptyList()
        }
        return findJvmBinaryClassAndLoadMemberAnnotations(signature).map {
            buildAnnotation {
                annotationTypeRef = it.annotationTypeRef
                argumentMapping = it.argumentMapping
                useSiteTarget = AnnotationUseSiteTarget.FIELD
            }
        }
    }

    override fun loadPropertyDelegatedFieldAnnotations(
        containerSource: DeserializedContainerSource?,
        propertyProto: ProtoBuf.Property,
        nameResolver: NameResolver,
        typeTable: TypeTable
    ): List<FirAnnotation> {
        val signature = getPropertySignature(propertyProto, nameResolver, typeTable, field = true) ?: return emptyList()
        if (!signature.isDelegated) {
            return emptyList()
        }
        return findJvmBinaryClassAndLoadMemberAnnotations(signature).map {
            buildAnnotation {
                annotationTypeRef = it.annotationTypeRef
                argumentMapping = it.argumentMapping
                useSiteTarget = AnnotationUseSiteTarget.PROPERTY_DELEGATE_FIELD
            }
        }
    }

    override fun loadPropertyGetterAnnotations(
        containerSource: DeserializedContainerSource?,
        propertyProto: ProtoBuf.Property,
        nameResolver: NameResolver,
        typeTable: TypeTable,
        getterFlags: Int
    ): List<FirAnnotation> {
        val signature = getCallableSignature(propertyProto, nameResolver, typeTable, CallableKind.PROPERTY_GETTER) ?: return emptyList()
        return findJvmBinaryClassAndLoadMemberAnnotations(signature)
    }

    override fun loadPropertySetterAnnotations(
        containerSource: DeserializedContainerSource?,
        propertyProto: ProtoBuf.Property,
        nameResolver: NameResolver,
        typeTable: TypeTable,
        setterFlags: Int
    ): List<FirAnnotation> {
        val signature = getCallableSignature(propertyProto, nameResolver, typeTable, CallableKind.PROPERTY_SETTER) ?: return emptyList()
        return findJvmBinaryClassAndLoadMemberAnnotations(signature)
    }

    override fun loadValueParameterAnnotations(
        containerSource: DeserializedContainerSource?,
        callableProto: MessageLite,
        valueParameterProto: ProtoBuf.ValueParameter,
        classProto: ProtoBuf.Class?,
        nameResolver: NameResolver,
        typeTable: TypeTable,
        kind: CallableKind,
        parameterIndex: Int,
    ): List<FirAnnotation> {
        val methodSignature = getCallableSignature(callableProto, nameResolver, typeTable, kind) ?: return emptyList()
        val index = parameterIndex + computeJvmParameterIndexShift(classProto, callableProto)
        val paramSignature = MemberSignature.fromMethodSignatureAndParameterIndex(methodSignature, index)
        return findJvmBinaryClassAndLoadMemberAnnotations(paramSignature)
    }

    override fun loadExtensionReceiverParameterAnnotations(
        containerSource: DeserializedContainerSource?,
        callableProto: MessageLite,
        nameResolver: NameResolver,
        typeTable: TypeTable,
        kind: CallableKind
    ): List<FirAnnotation> {
        val methodSignature = getCallableSignature(callableProto, nameResolver, typeTable, kind) ?: return emptyList()
        val paramSignature = MemberSignature.fromMethodSignatureAndParameterIndex(methodSignature, 0)
        return findJvmBinaryClassAndLoadMemberAnnotations(paramSignature)
    }

    private fun computeJvmParameterIndexShift(classProto: ProtoBuf.Class?, message: MessageLite): Int {
        return when (message) {
            is ProtoBuf.Function -> if (message.hasReceiver()) 1 else 0
            is ProtoBuf.Property -> if (message.hasReceiver()) 1 else 0
            is ProtoBuf.Constructor -> {
                assert(classProto != null) {
                    "Constructor call without information about enclosing Class: $message"
                }
                val kind = Flags.CLASS_KIND.get(classProto!!.flags) ?: ProtoBuf.Class.Kind.CLASS
                val isInner = Flags.IS_INNER.get(classProto.flags)
                when {
                    kind == ProtoBuf.Class.Kind.ENUM_CLASS -> 2
                    isInner -> 1
                    else -> 0
                }
            }
            else -> throw UnsupportedOperationException("Unsupported message: ${message::class.java}")
        }
    }

    private fun getCallableSignature(
        proto: MessageLite,
        nameResolver: NameResolver,
        typeTable: TypeTable,
        kind: CallableKind = CallableKind.OTHERS,
        requireHasFieldFlagForField: Boolean = false
    ): MemberSignature? {
        return when (proto) {
            is ProtoBuf.Constructor -> {
                MemberSignature.fromJvmMemberSignature(
                    JvmProtoBufUtil.getJvmConstructorSignature(proto, nameResolver, typeTable) ?: return null
                )
            }
            is ProtoBuf.Function -> {
                val signature = JvmProtoBufUtil.getJvmMethodSignature(proto, nameResolver, typeTable) ?: return null
                MemberSignature.fromJvmMemberSignature(signature)
            }
            is ProtoBuf.Property -> {
                val signature = proto.getExtensionOrNull(JvmProtoBuf.propertySignature) ?: return null
                when (kind) {
                    CallableKind.PROPERTY_GETTER ->
                        if (signature.hasGetter()) MemberSignature.fromMethod(nameResolver, signature.getter) else null
                    CallableKind.PROPERTY_SETTER ->
                        if (signature.hasSetter()) MemberSignature.fromMethod(nameResolver, signature.setter) else null
                    CallableKind.PROPERTY ->
                        getPropertySignature(
                            proto, nameResolver, typeTable,
                            field = true,
                            requireHasFieldFlagForField = requireHasFieldFlagForField
                        )
                    else ->
                        null
                }
            }
            else -> null
        }
    }

    private fun findJvmBinaryClassAndLoadMemberAnnotations(
        memberSignature: MemberSignature, searchInDefaultImpls: Boolean = false
    ): List<FirAnnotation> {
        val info = if (searchInDefaultImpls) {
            annotationInfoForDefaultImpls ?: return emptyList()
        } else {
            annotationInfo
        }
        return info.memberAnnotations[memberSignature] ?: emptyList()
    }
}

// TODO: Rename this once property constants are recorded as well
private data class MemberAnnotations(val memberAnnotations: MutableMap<MemberSignature, MutableList<FirAnnotation>>)

// TODO: better to be in KotlinDeserializedJvmSymbolsProvider?
private fun FirSession.loadMemberAnnotations(
    memberAnnotationNodes: Map<MemberSignature, List<JvmAnnotationNode>>,
    kotlinClassFinder: KotlinClassFinder,
): MemberAnnotations {
    val annotationsLoader = AnnotationsLoader(this, kotlinClassFinder)
    val memberAnnotations = memberAnnotationNodes.mapValuesTo(HashMap()) { entry ->
        val result = mutableListOf<FirAnnotation>()
        entry.value.map {
            it.accept(annotationsLoader.loadAnnotationIfNotSpecial(it.classId, result))
        }
        result
    }
    return MemberAnnotations(memberAnnotations)
}

private fun readMemberAnnotationNodes(
    kotlinBinaryClass: KotlinJvmBinaryClass,
    byteContent: ReusableByteArray?,
): Map<MemberSignature, List<JvmAnnotationNode>> {
    val memberAnnotations = HashMap<MemberSignature, MutableList<JvmAnnotationNode>>()

    kotlinBinaryClass.visitMembers(object : KotlinJvmBinaryClass.MemberVisitor {
        override fun visitMethod(name: Name, desc: String): KotlinJvmBinaryClass.MethodAnnotationVisitor {
            return AnnotationVisitorForMethod(MemberSignature.fromMethodNameAndDesc(name.asString(), desc))
        }

        override fun visitField(name: Name, desc: String, initializer: Any?): KotlinJvmBinaryClass.AnnotationVisitor {
            val signature = MemberSignature.fromFieldNameAndDesc(name.asString(), desc)
            if (initializer != null) {
                // TODO: load constant
            }
            return MemberAnnotationVisitor(signature)
        }

        inner class AnnotationVisitorForMethod(signature: MemberSignature) : MemberAnnotationVisitor(signature),
            KotlinJvmBinaryClass.MethodAnnotationVisitor {

            override fun visitParameterAnnotation(
                index: Int,
                classId: ClassId,
                source: SourceElement
            ): KotlinJvmBinaryClass.AnnotationArgumentVisitor {
                val paramSignature = MemberSignature.fromMethodSignatureAndParameterIndex(this.signature, index)
                return JvmAnnotationNode(classId).also { node ->
                    memberAnnotations.getOrPut(paramSignature) { ArrayList() }.add(node)
                }
            }

            override fun visitAnnotationMemberDefaultValue(): KotlinJvmBinaryClass.AnnotationArgumentVisitor? {
                // TODO: load annotation default values to properly support annotation instantiation feature
                return null
            }
        }

        open inner class MemberAnnotationVisitor(protected val signature: MemberSignature) : KotlinJvmBinaryClass.AnnotationVisitor {
            private val result = ArrayList<JvmAnnotationNode>()

            override fun visitAnnotation(classId: ClassId, source: SourceElement): KotlinJvmBinaryClass.AnnotationArgumentVisitor? {
                return JvmAnnotationNode(classId).also { result += it }
            }

            override fun visitEnd() {
                if (result.isNotEmpty()) {
                    memberAnnotations[signature] = result
                }
            }
        }
    }, byteContent)

    return memberAnnotations
}

private enum class Op { Visit, VisitClassLiteral, VisitEnum, VisitAnnotation, VisitArray }

class JvmAnnotationNode(val classId: ClassId) : KotlinJvmBinaryClass.AnnotationArgumentVisitor {
    private val a = ArrayList<Any?>()

    override fun visit(name: Name?, value: Any?) {
        a += Op.Visit
        a += name
        a += value
    }

    override fun visitClassLiteral(name: Name?, value: ClassLiteralValue) {
        a += Op.VisitClassLiteral
        a += name
        a += value
    }

    override fun visitEnum(name: Name?, enumClassId: ClassId, enumEntryName: Name) {
        a += Op.VisitEnum
        a += name
        a += enumClassId
        a += enumEntryName
    }

    override fun visitAnnotation(name: Name?, classId: ClassId): KotlinJvmBinaryClass.AnnotationArgumentVisitor {
        a += Op.VisitAnnotation
        a += name
        return JvmAnnotationNode(classId).also { a += it }
    }

    override fun visitArray(name: Name?): KotlinJvmBinaryClass.AnnotationArrayArgumentVisitor {
        a += Op.VisitArray
        a += name
        return JvmAnnotationArrayArgumentNode().also { a += it }
    }

    override fun visitEnd() {}

    fun accept(visitor: KotlinJvmBinaryClass.AnnotationArgumentVisitor?) {
        if (visitor == null) return
        var i = 0
        while (i < a.size) {
            when (a[i++]) {
                Op.Visit -> visitor.visit(a[i++] as Name?, a[i++])
                Op.VisitClassLiteral -> visitor.visitClassLiteral(a[i++] as Name?, a[i++] as ClassLiteralValue)
                Op.VisitEnum -> visitor.visitEnum(a[i++] as Name?, a[i++] as ClassId, a[i++] as Name)
                Op.VisitAnnotation -> {
                    val name = a[i++] as Name?
                    val node = a[i++] as JvmAnnotationNode
                    node.accept(visitor.visitAnnotation(name, node.classId))
                }
                Op.VisitArray -> {
                    val name = a[i++] as Name?
                    val node = a[i++] as JvmAnnotationArrayArgumentNode
                    node.accept(visitor.visitArray(name))
                }
            }
        }
        visitor.visitEnd()
    }
}

class JvmAnnotationArrayArgumentNode : KotlinJvmBinaryClass.AnnotationArrayArgumentVisitor {
    private val a = ArrayList<Any?>()

    override fun visit(value: Any?) {
        a += Op.Visit
        a += value
    }

    override fun visitEnum(enumClassId: ClassId, enumEntryName: Name) {
        a += Op.VisitEnum
        a += enumClassId
        a += enumEntryName
    }

    override fun visitClassLiteral(value: ClassLiteralValue) {
        a += Op.VisitClassLiteral
        a += value
    }

    override fun visitAnnotation(classId: ClassId): KotlinJvmBinaryClass.AnnotationArgumentVisitor {
        a += Op.VisitAnnotation
        return JvmAnnotationNode(classId).also { a += it }
    }

    override fun visitEnd() {}

    fun accept(visitor: KotlinJvmBinaryClass.AnnotationArrayArgumentVisitor?) {
        if (visitor == null) return
        var i = 0
        while (i < a.size) {
            when (a[i++]) {
                Op.Visit -> visitor.visit(a[i++])
                Op.VisitClassLiteral -> visitor.visitClassLiteral(a[i++] as ClassLiteralValue)
                Op.VisitEnum -> visitor.visitEnum(a[i++] as ClassId, a[i++] as Name)
                Op.VisitAnnotation -> {
                    val node = a[i++] as JvmAnnotationNode
                    node.accept(visitor.visitAnnotation(node.classId))
                }
            }
        }
        visitor.visitEnd()
    }
}