/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.common.serialization

import org.jetbrains.kotlin.ir.util.IdSignature
import org.jetbrains.kotlin.ir.util.IdSignature.FileSignature
import org.jetbrains.kotlin.backend.common.serialization.proto.AccessorIdSignature as ProtoAccessorIdSignature
import org.jetbrains.kotlin.backend.common.serialization.proto.CommonIdSignature as ProtoCommonIdSignature
import org.jetbrains.kotlin.backend.common.serialization.proto.CompositeSignature as ProtoCompositeSignature
import org.jetbrains.kotlin.backend.common.serialization.proto.FileLocalIdSignature as ProtoFileLocalIdSignature
import org.jetbrains.kotlin.backend.common.serialization.proto.FileSignature as ProtoFileSignature
import org.jetbrains.kotlin.backend.common.serialization.proto.IdSignature as ProtoIdSignature
import org.jetbrains.kotlin.backend.common.serialization.proto.LocalSignature as ProtoLocalSignature

/**
 * @see getFileSignature
 */
fun interface KlibFileSignatureProvider {

    /**
     * Used to build a signature of a private declaration.
     * Returns a signature of a file from the current module with the provided [fqName] and [fileName].
     * There may be multiple files with the same [fqName] and [fileName] in the same module. To distinguish them, [fileIdentifier]
     * may be used.
     *
     * @param fileIdentifier An identifier of the file in the current module, or -1 if the signature being deserialized doesn't contain
     * a file identifier.
     * @param fqName A fully-qualified name of the package the file belongs to.
     * @param fileName The name of the file.
     * @return A signature of a file from the current module.
     */
    fun getFileSignature(fileIdentifier: Int, fqName: String, fileName: String): FileSignature
}

class IdSignatureDeserializer(
    private val libraryFile: IrLibraryFile,
    private val currentFileSignature: FileSignature?,
    private val fileSignatureProvider: KlibFileSignatureProvider? = null,
) {

    private fun loadSignatureProto(index: Int): ProtoIdSignature {
        return libraryFile.signature(index)
    }

    private val signatureCache = HashMap<Int, IdSignature>()

    fun deserializeIdSignature(index: Int): IdSignature {
        return signatureCache.getOrPut(index) {
            val sigData = loadSignatureProto(index)
            deserializeSignatureData(sigData)
        }
    }

    private fun deserializePublicIdSignature(proto: ProtoCommonIdSignature): IdSignature.CommonSignature {
        val pkg = libraryFile.deserializeFqName(proto.packageFqNameList)
        val cls = libraryFile.deserializeFqName(proto.declarationFqNameList)
        val memberId = if (proto.hasMemberUniqId()) proto.memberUniqId else null

        return IdSignature.CommonSignature(pkg, cls, memberId, proto.flags)
    }

    private fun deserializeAccessorIdSignature(proto: ProtoAccessorIdSignature): IdSignature.AccessorSignature {
        val propertySignature = deserializeIdSignature(proto.propertySignature)
        require(propertySignature is IdSignature.CommonSignature) { "For public accessor corresponding property supposed to be public as well" }
        val name = libraryFile.string(proto.name)
        val hash = proto.accessorHashId
        val mask = proto.flags

        val accessorSignature =
            IdSignature.CommonSignature(propertySignature.packageFqName, "${propertySignature.declarationFqName}.$name", hash, mask)

        return IdSignature.AccessorSignature(propertySignature, accessorSignature)
    }

    private fun deserializeFileLocalIdSignature(proto: ProtoFileLocalIdSignature): IdSignature {
        return IdSignature.FileLocalSignature(deserializeIdSignature(proto.container), proto.localId)
    }

    private fun deserializeScopeLocalIdSignature(proto: Int): IdSignature {
        return IdSignature.ScopeLocalDeclaration(proto)
    }

    private fun deserializeCompositeIdSignature(proto: ProtoCompositeSignature): IdSignature.CompositeSignature {
        val containerSig = deserializeIdSignature(proto.containerSig)
        val innerSig = deserializeIdSignature(proto.innerSig)
        return IdSignature.CompositeSignature(containerSig, innerSig)
    }

    private fun deserializeLocalIdSignature(proto: ProtoLocalSignature): IdSignature.LocalSignature {
        val localFqn = libraryFile.deserializeFqName(proto.localFqNameList)
        val localHash = if (proto.hasLocalHash()) proto.localHash else null
        val description = if (proto.hasDebugInfo()) libraryFile.debugInfo(proto.debugInfo) else null
        return IdSignature.LocalSignature(localFqn, localHash, description)
    }

    private fun deserializeFileIdSignature(proto: ProtoFileSignature): FileSignature {
        requireNotNull(fileSignatureProvider) {
            "${(::fileSignatureProvider).name} is required for deserializing file signatures"
        }
        return if (proto.hasName()) {
            val fqName = libraryFile.deserializeFqName(proto.fqNameList)
            fileSignatureProvider.getFileSignature(proto.fileIdentifier, fqName, proto.name)
        } else {
            // Fallback for older behavior when we serialized file signatures as empty structures
            currentFileSignature ?: error("Provide file symbol")
        }
    }

    private fun deserializeSignatureData(proto: ProtoIdSignature): IdSignature {
        return when (proto.idSigCase) {
            ProtoIdSignature.IdSigCase.PUBLIC_SIG -> deserializePublicIdSignature(proto.publicSig)
            ProtoIdSignature.IdSigCase.ACCESSOR_SIG -> deserializeAccessorIdSignature(proto.accessorSig)
            ProtoIdSignature.IdSigCase.PRIVATE_SIG -> deserializeFileLocalIdSignature(proto.privateSig)
            ProtoIdSignature.IdSigCase.SCOPED_LOCAL_SIG -> deserializeScopeLocalIdSignature(proto.scopedLocalSig)
            ProtoIdSignature.IdSigCase.COMPOSITE_SIG -> deserializeCompositeIdSignature(proto.compositeSig)
            ProtoIdSignature.IdSigCase.LOCAL_SIG -> deserializeLocalIdSignature(proto.localSig)
            ProtoIdSignature.IdSigCase.FILE_SIG -> deserializeFileIdSignature(proto.fileSig)
            else -> error("Unexpected IdSignature kind: ${proto.idSigCase}")
        }
    }

    fun signatureToIndexMapping(): Map<IdSignature, Int> {
        return signatureCache.entries.associate { it.value to it.key }
    }
}
