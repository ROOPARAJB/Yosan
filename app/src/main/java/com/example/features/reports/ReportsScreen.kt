package com.example.features.reports

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.features.transactions.FinanceViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ReportsScreen(
    viewModel: FinanceViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val summary by viewModel.dashboardSummary.collectAsState()
    val categoryBreakdown by viewModel.categoryBreakdown.collectAsState()
    val categoryIncomeBreakdown by viewModel.categoryIncomeBreakdown.collectAsState()
    val monthlyTrends by viewModel.monthlyTrends.collectAsState()
    val loans by viewModel.loans.collectAsState()
    val allTransactions by viewModel.allTransactions.collectAsState()
    val companyExpenses by viewModel.companyExpenses.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    val isRevealed by viewModel.isAmountTemporarilyRevealed.collectAsState()
    val privacyEnabled = userProfile?.isPrivacyBlurEnabled ?: true
    val isMasked = privacyEnabled && !isRevealed

    var pdfPreviewBitmaps by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var currentPageIndex by remember { mutableIntStateOf(0) }
    var generatedFile by remember { mutableStateOf<File?>(null) }
    var isRendering by remember { mutableStateOf(true) }

    // Generate & Render PDF Preview
    LaunchedEffect(summary, categoryBreakdown, categoryIncomeBreakdown, monthlyTrends, loans, allTransactions, companyExpenses, userProfile?.name) {
        isRendering = true
        withContext(Dispatchers.IO) {
            try {
                val file = ExportService.generatePdfReport(
                    context = context,
                    summary = summary,
                    categoryExpenseBreakdown = categoryBreakdown,
                    categoryIncomeBreakdown = categoryIncomeBreakdown,
                    monthlyTrends = monthlyTrends,
                    loans = loans,
                    allTransactions = allTransactions,
                    companyExpenses = companyExpenses,
                    userName = userProfile?.name
                )
                generatedFile = file

                val bitmaps = mutableListOf<Bitmap>()
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        for (i in 0 until renderer.pageCount) {
                            renderer.openPage(i).use { page ->
                                val width = (page.width * 1.5f).toInt()
                                val height = (page.height * 1.5f).toInt()
                                val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                                val canvas = Canvas(bmp)
                                canvas.drawColor(android.graphics.Color.WHITE)
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                bitmaps.add(bmp)
                            }
                        }
                    }
                }
                pdfPreviewBitmaps = bitmaps
                if (currentPageIndex >= bitmaps.size) {
                    currentPageIndex = 0
                }
            } catch (_: Exception) {}
            isRendering = false
        }
    }

    fun sharePdfReport() {
        try {
            val file = generatedFile ?: ExportService.generatePdfReport(
                context = context,
                summary = summary,
                categoryExpenseBreakdown = categoryBreakdown,
                categoryIncomeBreakdown = categoryIncomeBreakdown,
                monthlyTrends = monthlyTrends,
                loans = loans,
                allTransactions = allTransactions,
                companyExpenses = companyExpenses,
                userName = userProfile?.name
            )
            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, file)
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(sendIntent, "Share PDF Report").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            if (com.example.BuildConfig.DEBUG) {
                android.util.Log.e("ReportsScreen", "Could not share PDF", e)
            }
            viewModel.showMessage("Could not share PDF: ${e.localizedMessage ?: "Please install a PDF sharing app"}")
        }
    }

    fun openPdfReport() {
        try {
            val file = generatedFile ?: ExportService.generatePdfReport(
                context = context,
                summary = summary,
                categoryExpenseBreakdown = categoryBreakdown,
                categoryIncomeBreakdown = categoryIncomeBreakdown,
                monthlyTrends = monthlyTrends,
                loans = loans,
                allTransactions = allTransactions,
                companyExpenses = companyExpenses,
                userName = userProfile?.name
            )
            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, file)
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(viewIntent, "Open PDF Report").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            if (com.example.BuildConfig.DEBUG) {
                android.util.Log.e("ReportsScreen", "Could not open PDF", e)
            }
            viewModel.showMessage("Could not open PDF: ${e.localizedMessage ?: "Please install a PDF viewer app"}")
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("reports_screen"),
        contentPadding = PaddingValues(bottom = 100.dp)
    ) {
        // 1. Header & Actions
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "Consolidated income, expense, and financial statement performance report.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { sharePdfReport() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share PDF")
                    }

                    OutlinedButton(
                        onClick = { openPdfReport() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open File")
                    }
                }
            }
        }

        // 2. In-App Live PDF Document Preview
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Document Preview",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (isMasked) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.clickable { viewModel.revealAmountsTemporarily() }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Blurred • Tap to reveal",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else if (pdfPreviewBitmaps.size > 1) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "Page ${currentPageIndex + 1} of ${pdfPreviewBitmaps.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    } else {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "A4 Format • Live",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (isRendering && pdfPreviewBitmaps.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(420.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                } else if (pdfPreviewBitmaps.isNotEmpty()) {
                    // Multi-page navigation controls
                    if (pdfPreviewBitmaps.size > 1) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilledTonalButton(
                                onClick = { if (currentPageIndex > 0) currentPageIndex-- },
                                enabled = currentPageIndex > 0,
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous Page", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Previous")
                            }

                            Text(
                                text = "Page ${currentPageIndex + 1} / ${pdfPreviewBitmaps.size}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            FilledTonalButton(
                                onClick = { if (currentPageIndex < pdfPreviewBitmaps.size - 1) currentPageIndex++ },
                                enabled = currentPageIndex < pdfPreviewBitmaps.size - 1,
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("Next")
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Page", modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    val currentBmp = pdfPreviewBitmaps.getOrNull(currentPageIndex) ?: pdfPreviewBitmaps.first()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(enabled = isMasked) { viewModel.revealAmountsTemporarily() }
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(6.dp, RoundedCornerShape(16.dp))
                                .clip(RoundedCornerShape(16.dp))
                                .blur(if (isMasked) 28.dp else 0.dp),
                            colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp)
                            ) {
                                Image(
                                    bitmap = currentBmp.asImageBitmap(),
                                    contentDescription = "PDF Report Preview Page ${currentPageIndex + 1}",
                                    contentScale = ContentScale.FillWidth,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                )
                            }
                        }

                        if (isMasked) {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.78f))
                                    .clickable { viewModel.revealAmountsTemporarily() }
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = CircleShape,
                                        modifier = Modifier.size(60.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.VisibilityOff,
                                                contentDescription = "Hidden by Privacy Blur",
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.size(30.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Text(
                                        text = "Financial Privacy Mode Active",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Tap anywhere to reveal report preview",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Multi-page thumbnail strip for fast page switching
                    if (pdfPreviewBitmaps.size > 1) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "All Pages (${pdfPreviewBitmaps.size})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            itemsIndexed(pdfPreviewBitmaps) { idx, bmp ->
                                val isSelected = idx == currentPageIndex
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            if (isMasked) viewModel.revealAmountsTemporarily()
                                            currentPageIndex = idx
                                        }
                                        .border(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .padding(4.dp)
                                ) {
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = "Thumbnail Page ${idx + 1}",
                                        modifier = Modifier
                                            .height(80.dp)
                                            .width(56.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .blur(if (isMasked) 16.dp else 0.dp),
                                        contentScale = ContentScale.Fit
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Page ${idx + 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


