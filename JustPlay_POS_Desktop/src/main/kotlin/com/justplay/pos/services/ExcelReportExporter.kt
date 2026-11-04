package com.justplay.pos.services

import com.justplay.pos.core.models.Reservation
import com.justplay.pos.core.models.Room
import com.justplay.pos.core.models.RoomState
import com.justplay.pos.core.models.SaleTransaction
import com.justplay.pos.core.models.SessionRecord
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Servicio de exportación de informes y registros en formato Excel nativo (.xlsx)
 * y formato CSV compatible con Microsoft Excel (con BOM UTF-8 y delimitadores estándar).
 * 
 * Basado estrictamente en OpenXML (ECMA-376 / ISO-29500) para máxima compatibilidad
 * con Microsoft Excel 2010+, Google Sheets y LibreOffice, sin dependencias externas pesadas.
 */
object ExcelReportExporter {

    private fun sanitizeXml(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    /**
     * Construye un archivo OpenXML .xlsx nativo a partir de filas de cabecera y datos.
     */
    fun createXlsxFile(
        outputFile: File,
        sheetName: String,
        headers: List<String>,
        rows: List<List<Any?>>
    ): File {
        outputFile.parentFile?.mkdirs()

        FileOutputStream(outputFile).use { fos ->
            ZipOutputStream(fos).use { zos ->
                // 1. [Content_Types].xml
                zos.putNextEntry(ZipEntry("[Content_Types].xml"))
                val contentTypes = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>""".trimIndent()
                zos.write(contentTypes.toByteArray(StandardCharsets.UTF_8))
                zos.closeEntry()

                // 2. _rels/.rels
                zos.putNextEntry(ZipEntry("_rels/.rels"))
                val rels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>""".trimIndent()
                zos.write(rels.toByteArray(StandardCharsets.UTF_8))
                zos.closeEntry()

                // 3. xl/_rels/workbook.xml.rels
                zos.putNextEntry(ZipEntry("xl/_rels/workbook.xml.rels"))
                val wbRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>""".trimIndent()
                zos.write(wbRels.toByteArray(StandardCharsets.UTF_8))
                zos.closeEntry()

                // 4. xl/workbook.xml
                zos.putNextEntry(ZipEntry("xl/workbook.xml"))
                val safeSheetName = sanitizeXml(sheetName.take(31))
                val workbookXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="$safeSheetName" sheetId="1" r:id="rId1"/>
  </sheets>
</workbook>""".trimIndent()
                zos.write(workbookXml.toByteArray(StandardCharsets.UTF_8))
                zos.closeEntry()

                // 5. xl/styles.xml
                zos.putNextEntry(ZipEntry("xl/styles.xml"))
                val stylesXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="2">
    <font><sz val="11"/><name val="Calibri"/></font>
    <font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>
  </fonts>
  <fills count="3">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FF1E2838"/></patternFill></fill>
  </fills>
  <borders count="1">
    <border><left/><right/><top/><bottom/></border>
  </borders>
  <cellStyleXfs count="1">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0"/>
  </cellStyleXfs>
  <cellXfs count="2">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
    <xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/>
  </cellXfs>
</styleSheet>""".trimIndent()
                zos.write(stylesXml.toByteArray(StandardCharsets.UTF_8))
                zos.closeEntry()

                // 6. xl/worksheets/sheet1.xml
                zos.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
                val sb = StringBuilder()
                sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
                sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
                sb.append("<sheetData>")

                // Fila 1: Cabeceras
                sb.append("""<row r="1">""")
                headers.forEachIndexed { colIndex, header ->
                    val colLetter = getColumnLetter(colIndex)
                    sb.append("""<c r="${colLetter}1" s="1" t="inlineStr"><is><t>${sanitizeXml(header)}</t></is></c>""")
                }
                sb.append("</row>")

                // Filas de Datos
                rows.forEachIndexed { rIndex, rowData ->
                    val rowNum = rIndex + 2
                    sb.append("""<row r="$rowNum">""")
                    rowData.forEachIndexed { colIndex, cellVal ->
                        val colLetter = getColumnLetter(colIndex)
                        val cellRef = "$colLetter$rowNum"
                        when (cellVal) {
                            null -> {
                                sb.append("""<c r="$cellRef"/>""")
                            }
                            is Number -> {
                                sb.append("""<c r="$cellRef" t="n"><v>$cellVal</v></c>""")
                            }
                            is Boolean -> {
                                sb.append("""<c r="$cellRef" t="b"><v>${if (cellVal) 1 else 0}</v></c>""")
                            }
                            else -> {
                                val text = sanitizeXml(cellVal.toString())
                                sb.append("""<c r="$cellRef" t="inlineStr"><is><t>$text</t></is></c>""")
                            }
                        }
                    }
                    sb.append("</row>")
                }

                sb.append("</sheetData>")
                sb.append("</worksheet>")

                zos.write(sb.toString().toByteArray(StandardCharsets.UTF_8))
                zos.closeEntry()
            }
        }
        return outputFile
    }

    private fun getColumnLetter(colIndex: Int): String {
        var temp = colIndex
        val sb = StringBuilder()
        while (temp >= 0) {
            sb.insert(0, ('A'.code + (temp % 26)).toChar())
            temp = (temp / 26) - 1
        }
        return sb.toString()
    }

    /**
     * Genera un archivo CSV con UTF-8 BOM (\uFEFF) para visualización inmediata en Microsoft Excel.
     */
    fun createExcelCompatibleCsv(
        outputFile: File,
        headers: List<String>,
        rows: List<List<Any?>>
    ): File {
        outputFile.parentFile?.mkdirs()
        FileOutputStream(outputFile).use { fos ->
            OutputStreamWriter(fos, StandardCharsets.UTF_8).use { writer ->
                // Escribir Byte Order Mark (BOM) para que Excel detecte UTF-8 sin caracteres extraños
                writer.write("\uFEFF")
                // Escribir directiva de separación
                writer.write("sep=;\n")

                // Cabeceras
                writer.write(headers.joinToString(";") { escapeCsv(it) })
                writer.write("\n")

                // Filas
                for (row in rows) {
                    val line = row.joinToString(";") { cell ->
                        escapeCsv(cell?.toString() ?: "")
                    }
                    writer.write(line)
                    writer.write("\n")
                }
            }
        }
        return outputFile
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(";") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
    }

    /**
     * Exporta el Historial de Ventas Locales a Excel (.xlsx y .csv).
     */
    fun exportSales(sales: List<SaleTransaction>, baseDir: File = getDefaultExportDir()): Pair<File, File> {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val xlsxFile = File(baseDir, "JustPlay_Ventas_$timestamp.xlsx")
        val csvFile = File(baseDir, "JustPlay_Ventas_$timestamp.csv")

        val headers = listOf(
            "ID Venta",
            "Fecha y Hora",
            "Método de Pago",
            "Total (Bs)",
            "Efectivo Recibido (Bs)",
            "Monto QR (Bs)",
            "Cambio Devuelto (Bs)",
            "Cliente",
            "Celular",
            "Operador",
            "Sincronizado Cloud",
            "Detalle Artículos"
        )

        val rows = sales.map { sale ->
            listOf(
                sale.id,
                sale.dateTime,
                sale.paymentMethod.displayName,
                sale.totalAmount,
                sale.cashReceived,
                sale.qrAmount,
                sale.changeReturned,
                sale.clientName,
                sale.clientPhone ?: "S/N",
                sale.operatorName,
                if (sale.isSynced) "Sí" else "Pendiente",
                sale.formattedSummary
            )
        }

        createXlsxFile(xlsxFile, "Ventas", headers, rows)
        createExcelCompatibleCsv(csvFile, headers, rows)

        return Pair(xlsxFile, csvFile)
    }

    /**
     * Exporta el Registro Histórico de Sesiones y Alquileres de Salas a Excel.
     */
    fun exportSessions(records: List<SessionRecord>, baseDir: File = getDefaultExportDir()): Pair<File, File> {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val xlsxFile = File(baseDir, "JustPlay_Sesiones_$timestamp.xlsx")
        val csvFile = File(baseDir, "JustPlay_Sesiones_$timestamp.csv")

        val headers = listOf(
            "ID Firebase",
            "Fecha y Hora",
            "Tipo",
            "Sala / Recurso",
            "Cliente",
            "Celular",
            "Película / Juego",
            "Tiempo Configurado",
            "Total Cobrado (Bs)",
            "Método de Pago",
            "Registrado Por",
            "Pedidos / Consumos"
        )

        val rows = records.map { rec ->
            listOf(
                rec.idFirebase,
                rec.fechaHora,
                rec.tipo,
                rec.nombreElemento,
                rec.cliente,
                rec.celular ?: "S/N",
                rec.peliculaJuego ?: "N/A",
                rec.tiempoConfigurado ?: "N/A",
                rec.totalCobrado,
                rec.metodoPago,
                "Sistema",
                rec.pedidos
            )
        }

        createXlsxFile(xlsxFile, "Sesiones", headers, rows)
        createExcelCompatibleCsv(csvFile, headers, rows)

        return Pair(xlsxFile, csvFile)
    }

    /**
     * Exporta las Reservas Realizadas a Excel.
     */
    fun exportReservations(reservations: List<Reservation>, baseDir: File = getDefaultExportDir()): Pair<File, File> {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val xlsxFile = File(baseDir, "JustPlay_Reservas_$timestamp.xlsx")
        val csvFile = File(baseDir, "JustPlay_Reservas_$timestamp.csv")

        val headers = listOf(
            "ID Reserva",
            "Fecha Programada",
            "Hora Inicio",
            "Hora Salida Estimada",
            "Duración",
            "Cliente",
            "Celular",
            "Tipo de Sala",
            "Cantidad Volantes",
            "Adelanto (Bs)",
            "Método Adelanto",
            "Tiene Comprobante",
            "Estado / Asistencia"
        )

        val rows = reservations.map { res ->
            listOf(
                res.idFirebase,
                res.fecha,
                res.horaProgramada,
                res.calcularHoraFin(),
                res.duracion,
                res.nombre,
                res.celular,
                res.tipoSala,
                res.cantidadVolantes,
                res.adelanto.toDoubleOrNull() ?: 0.0,
                res.metodoPagoAdelanto,
                if (res.comprobanteUri != null) "Sí" else "No",
                if (res.asistio) "Asistió (${res.horaLlegada ?: ""})" else "Pendiente"
            )
        }

        createXlsxFile(xlsxFile, "Reservas", headers, rows)
        createExcelCompatibleCsv(csvFile, headers, rows)

        return Pair(xlsxFile, csvFile)
    }

    /**
     * Exporta el Reporte General Consolidado de todo el sistema (Cine, Volantes, Just Dance, Reservas y Ventas).
     */
    fun exportMasterReport(
        rooms: List<Room>,
        reservations: List<Reservation>,
        records: List<SessionRecord>,
        sales: List<SaleTransaction>,
        baseDir: File = getDefaultExportDir()
    ): Pair<File, File> {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val xlsxFile = File(baseDir, "JustPlay_Reporte_General_Completo_$timestamp.xlsx")
        val csvFile = File(baseDir, "JustPlay_Reporte_General_Completo_$timestamp.csv")

        val headers = listOf(
            "Categoría / Módulo",
            "Identificador",
            "Fecha y Hora",
            "Recurso / Sala",
            "Cliente",
            "Celular",
            "Detalle / Película / Artículos",
            "Duración / Cantidad",
            "Total Cobrado (Bs)",
            "Método de Pago",
            "Estado / Tipo"
        )

        val rows = mutableListOf<List<Any?>>()

        // 1. Sesiones Activas en Salas
        rooms.filter { it.estado == RoomState.OCUPADA }.forEach { room ->
            rows.add(
                listOf(
                    room.categoria,
                    "SALA_${room.id}",
                    room.obtenerHoraInicioFormateada(),
                    room.nombre,
                    room.cliente ?: "N/A",
                    room.celular ?: "S/N",
                    room.pelicula ?: "En sesión",
                    room.formatearTiempoRestante(),
                    room.precioCobrado,
                    room.metodoPago,
                    "Sesión Activa"
                )
            )
        }

        // 2. Registros Históricos de Sesiones (Minicine, Volantes, Just Dance)
        records.forEach { rec ->
            rows.add(
                listOf(
                    rec.tipo,
                    rec.idFirebase,
                    rec.fechaHora,
                    rec.nombreElemento,
                    rec.cliente,
                    rec.celular ?: "S/N",
                    rec.peliculaJuego ?: rec.pedidos,
                    rec.tiempoConfigurado ?: "N/A",
                    rec.totalCobrado,
                    rec.metodoPago,
                    "Histórico Finalizado"
                )
            )
        }

        // 3. Reservas Programadas
        reservations.forEach { res ->
            rows.add(
                listOf(
                    "Reserva",
                    res.idFirebase,
                    "${res.fecha} ${res.horaProgramada}",
                    res.tipoSala,
                    res.nombre,
                    res.celular,
                    "Adelanto: ${res.adelanto} Bs",
                    res.duracion,
                    res.adelanto.toDoubleOrNull() ?: 0.0,
                    res.metodoPagoAdelanto,
                    if (res.asistio) "Asistió" else "Pendiente"
                )
            )
        }

        // 4. Ventas de Mostrador (Snacks, Bebidas, etc.)
        sales.forEach { sale ->
            rows.add(
                listOf(
                    "Venta POS",
                    sale.id,
                    sale.dateTime,
                    "Mostrador",
                    sale.clientName,
                    sale.clientPhone ?: "S/N",
                    sale.formattedSummary,
                    "${sale.items.sumOf { it.quantity }} items",
                    sale.totalAmount,
                    sale.paymentMethod.displayName,
                    if (sale.isSynced) "Sincronizado" else "Pendiente Sync"
                )
            )
        }

        createXlsxFile(xlsxFile, "Reporte General", headers, rows)
        createExcelCompatibleCsv(csvFile, headers, rows)

        return Pair(xlsxFile, csvFile)
    }

    /**
     * Obtiene el directorio de exportación predeterminado del sistema (Escritorio o Descargas del usuario).
     */
    fun getDefaultExportDir(): File {
        val userHome = File(System.getProperty("user.home"))
        val desktop = File(userHome, "Desktop")
        if (desktop.exists() && desktop.isDirectory && desktop.canWrite()) {
            val exportDir = File(desktop, "JustPlay_Reportes")
            exportDir.mkdirs()
            return exportDir
        }
        val downloads = File(userHome, "Downloads")
        if (downloads.exists() && downloads.isDirectory && downloads.canWrite()) {
            val exportDir = File(downloads, "JustPlay_Reportes")
            exportDir.mkdirs()
            return exportDir
        }
        val fallback = File(userHome, "JustPlay_Reportes")
        fallback.mkdirs()
        return fallback
    }
}
