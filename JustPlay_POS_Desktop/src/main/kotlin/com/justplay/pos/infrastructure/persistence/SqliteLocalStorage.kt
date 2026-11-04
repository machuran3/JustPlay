package com.justplay.pos.infrastructure.persistence

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.justplay.pos.core.interfaces.ILocalStorage
import com.justplay.pos.core.models.CartItem
import com.justplay.pos.core.models.PaymentMethod
import com.justplay.pos.core.models.Product
import com.justplay.pos.core.models.SaleTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

class SqliteLocalStorage(
    dbDirectory: File = File(System.getProperty("user.home"), ".justplay_pos")
) : ILocalStorage {

    private val dbFile = File(dbDirectory, "pos_local.db")
    private val connectionUrl = "jdbc:sqlite:${dbFile.absolutePath}"
    private val gson = Gson()

    init {
        if (!dbDirectory.exists()) {
            dbDirectory.mkdirs()
        }
        createTablesIfNotExist()
    }

    private fun getConnection(): Connection {
        return DriverManager.getConnection(connectionUrl)
    }

    private fun createTablesIfNotExist() {
        try {
            getConnection().use { conn ->
                conn.createStatement().use { stmt ->
                    // Tabla de Catálogo de Productos en Caché Local
                    stmt.execute(
                        """
                        CREATE TABLE IF NOT EXISTS products_cache (
                            barcode TEXT PRIMARY KEY,
                            name TEXT NOT NULL,
                            gramaje TEXT DEFAULT '',
                            category TEXT,
                            unit_price REAL NOT NULL,
                            cost_price REAL DEFAULT 0,
                            current_stock INTEGER DEFAULT 0,
                            min_stock_alert INTEGER DEFAULT 5,
                            active INTEGER DEFAULT 1,
                            unit_of_measure TEXT DEFAULT 'Unidad',
                            last_updated TEXT
                        )
                        """.trimIndent()
                    )
                    try {
                        stmt.execute("ALTER TABLE products_cache ADD COLUMN gramaje TEXT DEFAULT ''")
                    } catch (_: Exception) {}

                    // Tabla de Ventas Locales y Cola Offline
                    stmt.execute(
                        """
                        CREATE TABLE IF NOT EXISTS local_sales (
                            id TEXT PRIMARY KEY,
                            date_time TEXT NOT NULL,
                            items_json TEXT NOT NULL,
                            total_amount REAL NOT NULL,
                            payment_method TEXT NOT NULL,
                            cash_received REAL DEFAULT 0,
                            qr_amount REAL DEFAULT 0,
                            change_returned REAL DEFAULT 0,
                            client_name TEXT,
                            client_phone TEXT,
                            operator_name TEXT,
                            is_synced INTEGER DEFAULT 0,
                            is_deleted INTEGER DEFAULT 0
                        )
                        """.trimIndent()
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override suspend fun initialize(): Unit = withContext(Dispatchers.IO) {
        createTablesIfNotExist()
    }

    override suspend fun saveSale(sale: SaleTransaction): Unit = withContext(Dispatchers.IO) {
        createTablesIfNotExist()
        val itemsJson = gson.toJson(sale.items)
        getConnection().use { conn ->
            val sql = """
                INSERT OR REPLACE INTO local_sales (
                    id, date_time, items_json, total_amount, payment_method,
                    cash_received, qr_amount, change_returned, client_name, client_phone,
                    operator_name, is_synced, is_deleted
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()

            conn.prepareStatement(sql).use { pstmt ->
                pstmt.setString(1, sale.id)
                pstmt.setString(2, sale.dateTime)
                pstmt.setString(3, itemsJson)
                pstmt.setDouble(4, sale.totalAmount)
                pstmt.setString(5, sale.paymentMethod.name)
                pstmt.setDouble(6, sale.cashReceived)
                pstmt.setDouble(7, sale.qrAmount)
                pstmt.setDouble(8, sale.changeReturned)
                pstmt.setString(9, sale.clientName)
                pstmt.setString(10, sale.clientPhone)
                pstmt.setString(11, sale.operatorName)
                pstmt.setInt(12, if (sale.isSynced) 1 else 0)
                pstmt.setInt(13, if (sale.isDeleted) 1 else 0)
                pstmt.executeUpdate()
            }
        }
    }

    override suspend fun getUnsyncedSales(): List<SaleTransaction> = withContext(Dispatchers.IO) {
        createTablesIfNotExist()
        val list = mutableListOf<SaleTransaction>()
        getConnection().use { conn ->
            val sql = "SELECT * FROM local_sales WHERE is_synced = 0 ORDER BY date_time ASC"
            conn.createStatement().use { stmt ->
                val rs = stmt.executeQuery(sql)
                while (rs.next()) {
                    list.add(mapResultSetToSale(rs))
                }
            }
        }
        list
    }

    override suspend fun markSaleAsSynced(saleId: String): Unit = withContext(Dispatchers.IO) {
        createTablesIfNotExist()
        getConnection().use { conn ->
            val sql = "UPDATE local_sales SET is_synced = 1 WHERE id = ?"
            conn.prepareStatement(sql).use { pstmt ->
                pstmt.setString(1, saleId)
                pstmt.executeUpdate()
            }
        }
    }

    override suspend fun getAllSales(limit: Int): List<SaleTransaction> = withContext(Dispatchers.IO) {
        createTablesIfNotExist()
        val list = mutableListOf<SaleTransaction>()
        getConnection().use { conn ->
            val sql = "SELECT * FROM local_sales ORDER BY date_time DESC LIMIT ?"
            conn.prepareStatement(sql).use { pstmt ->
                pstmt.setInt(1, limit)
                val rs = pstmt.executeQuery()
                while (rs.next()) {
                    list.add(mapResultSetToSale(rs))
                }
            }
        }
        list
    }

    override suspend fun cacheProducts(products: List<Product>): Unit = withContext(Dispatchers.IO) {
        createTablesIfNotExist()
        getConnection().use { conn ->
            conn.autoCommit = false
            val sql = """
                INSERT OR REPLACE INTO products_cache (
                    barcode, name, gramaje, category, unit_price, cost_price,
                    current_stock, min_stock_alert, active, unit_of_measure, last_updated
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()

            conn.prepareStatement(sql).use { pstmt ->
                for (p in products) {
                    pstmt.setString(1, p.barcode)
                    pstmt.setString(2, p.name)
                    pstmt.setString(3, p.gramaje)
                    pstmt.setString(4, p.category)
                    pstmt.setDouble(5, p.unitPrice)
                    pstmt.setDouble(6, p.costPrice)
                    pstmt.setInt(7, p.currentStock)
                    pstmt.setInt(8, p.minStockAlert)
                    pstmt.setInt(9, if (p.active) 1 else 0)
                    pstmt.setString(10, p.unitOfMeasure)
                    pstmt.setString(11, p.lastUpdated)
                    pstmt.addBatch()
                }
                pstmt.executeBatch()
            }
            conn.commit()
        }
    }

    override suspend fun getCachedProducts(): List<Product> = withContext(Dispatchers.IO) {
        createTablesIfNotExist()
        val list = mutableListOf<Product>()
        getConnection().use { conn ->
            val sql = "SELECT * FROM products_cache WHERE active = 1 ORDER BY name ASC"
            conn.createStatement().use { stmt ->
                val rs = stmt.executeQuery(sql)
                while (rs.next()) {
                    val gramaje = try { rs.getString("gramaje") ?: "" } catch (_: Exception) { "" }
                    list.add(
                        Product(
                            barcode = rs.getString("barcode"),
                            name = rs.getString("name"),
                            gramaje = gramaje,
                            category = rs.getString("category") ?: "Snacks & Bebidas",
                            unitPrice = rs.getDouble("unit_price"),
                            costPrice = rs.getDouble("cost_price"),
                            currentStock = rs.getInt("current_stock"),
                            minStockAlert = rs.getInt("min_stock_alert"),
                            active = rs.getInt("active") == 1,
                            unitOfMeasure = rs.getString("unit_of_measure") ?: "Unidad",
                            lastUpdated = rs.getString("last_updated") ?: ""
                        )
                    )
                }
            }
        }
        list
    }

    private fun mapResultSetToSale(rs: java.sql.ResultSet): SaleTransaction {
        val itemsType = object : TypeToken<List<CartItem>>() {}.type
        val items: List<CartItem> = gson.fromJson(rs.getString("items_json"), itemsType) ?: emptyList()
        val paymentMethodStr = rs.getString("payment_method") ?: "EFECTIVO"
        val paymentMethod = try {
            PaymentMethod.valueOf(paymentMethodStr)
        } catch (_: Exception) {
            PaymentMethod.EFECTIVO
        }

        return SaleTransaction(
            id = rs.getString("id"),
            dateTime = rs.getString("date_time"),
            items = items,
            totalAmount = rs.getDouble("total_amount"),
            paymentMethod = paymentMethod,
            cashReceived = rs.getDouble("cash_received"),
            qrAmount = rs.getDouble("qr_amount"),
            changeReturned = rs.getDouble("change_returned"),
            clientName = rs.getString("client_name") ?: "Venta Mostrador",
            clientPhone = rs.getString("client_phone"),
            operatorName = rs.getString("operator_name") ?: "Lector POS PC",
            isSynced = rs.getInt("is_synced") == 1,
            isDeleted = rs.getInt("is_deleted") == 1
        )
    }
}
