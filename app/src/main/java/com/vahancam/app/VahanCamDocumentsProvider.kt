package com.vahancam.app

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import java.io.File

class VahanCamDocumentsProvider : DocumentsProvider() {

    companion object {
        const val AUTHORITY = "com.vahancam.app.documents"
        private const val ROOT_ID = "root_vahancam"
        private const val DOC_ID_ROOT = "doc_root"

        private val DEFAULT_ROOT_PROJECTION = arrayOf(
            DocumentsContract.Root.COLUMN_ROOT_ID,
            DocumentsContract.Root.COLUMN_FLAGS,
            DocumentsContract.Root.COLUMN_TITLE,
            DocumentsContract.Root.COLUMN_DOCUMENT_ID,
            DocumentsContract.Root.COLUMN_MIME_TYPES,
            DocumentsContract.Root.COLUMN_ICON
        )

        private val DEFAULT_DOCUMENT_PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_FLAGS,
            DocumentsContract.Document.COLUMN_SIZE
        )
    }

    override fun onCreate(): Boolean {
        return true
    }

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val result = MatrixCursor(projection ?: DEFAULT_ROOT_PROJECTION)
        val row = result.newRow()
        row.add(DocumentsContract.Root.COLUMN_ROOT_ID, ROOT_ID)
        row.add(DocumentsContract.Root.COLUMN_FLAGS, DocumentsContract.Root.FLAG_LOCAL_ONLY)
        row.add(DocumentsContract.Root.COLUMN_TITLE, "VahanCam Documents")
        row.add(DocumentsContract.Root.COLUMN_DOCUMENT_ID, DOC_ID_ROOT)
        row.add(DocumentsContract.Root.COLUMN_MIME_TYPES, "*/*")
        row.add(DocumentsContract.Root.COLUMN_ICON, R.drawable.ic_launcher_foreground)
        return result
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        val result = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        val context = context ?: return result

        if (documentId == DOC_ID_ROOT) {
            val row = result.newRow()
            row.add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DOC_ID_ROOT)
            row.add(DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.MIME_TYPE_DIR)
            row.add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, "VahanCam")
            row.add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, System.currentTimeMillis())
            row.add(DocumentsContract.Document.COLUMN_FLAGS, 0)
            row.add(DocumentsContract.Document.COLUMN_SIZE, 0)
        } else {
            val file = File(context.cacheDir, documentId)
            if (file.exists()) {
                val row = result.newRow()
                row.add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, file.name)
                row.add(DocumentsContract.Document.COLUMN_MIME_TYPE, "image/jpeg")
                row.add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, file.name)
                row.add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, file.lastModified())
                row.add(DocumentsContract.Document.COLUMN_FLAGS, 0)
                row.add(DocumentsContract.Document.COLUMN_SIZE, file.length())
            }
        }
        return result
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val result = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        val context = context ?: return result

        val cacheFiles = context.cacheDir.listFiles { f ->
            f.isFile && (f.name.endsWith(".jpg", ignoreCase = true) || f.name.endsWith(".jpeg", ignoreCase = true))
        } ?: emptyArray()

        // Sort latest first
        cacheFiles.sortByDescending { it.lastModified() }

        for (file in cacheFiles) {
            val row = result.newRow()
            row.add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, file.name)
            row.add(DocumentsContract.Document.COLUMN_MIME_TYPE, "image/jpeg")
            row.add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, file.name)
            row.add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, file.lastModified())
            row.add(DocumentsContract.Document.COLUMN_FLAGS, 0)
            row.add(DocumentsContract.Document.COLUMN_SIZE, file.length())
        }

        return result
    }

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?
    ): ParcelFileDescriptor {
        val context = context ?: throw IllegalStateException("Context is null")
        val file = File(context.cacheDir, documentId)
        if (!file.exists()) {
            throw IllegalArgumentException("Document not found: $documentId")
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }
}
