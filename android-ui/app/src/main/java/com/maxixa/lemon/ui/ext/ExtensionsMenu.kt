package com.maxixa.lemon.ui.ext

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.maxixa.lemon.ExtHost

@Composable
fun ExtensionsMenu(
    onDismiss: () -> Unit,
    onApply: (id: String, params: Map<String, Float>) -> Unit,
    kindFilter: String? = "editor",
) {
    val exts = remember { ExtHost.list().let { ls ->
        if (kindFilter == null) ls else ls.filter { it.kind == kindFilter }
    } }
    var selected by remember { mutableStateOf<ExtHost.Info?>(null) }
    val cur = selected
    if (cur == null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Extensions") },
            text = {
                if (exts.isEmpty()) Text("No extensions installed.")
                else LazyColumn {
                    items(exts) { e ->
                        ListItem(
                            headlineContent = { Text(e.name) },
                            supportingContent = { Text(
                                if (e.description.isBlank()) "v${e.version} · ${e.kind}"
                                else "${e.description} · v${e.version}"
                            ) },
                            modifier = Modifier.clickable { selected = e },
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        )
    } else {
        var params by remember(cur) {
            mutableStateOf(cur.params.associate { it.id to it.default }.toMutableMap())
        }
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(cur.name) },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    if (cur.description.isNotBlank()) {
                        Text(cur.description, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(12.dp))
                    }
                    cur.params.forEach { p ->
                        val v = params[p.id] ?: p.default
                        Text("${p.name}: ${"%.2f".format(v)}${if (p.unit.isNotEmpty()) " ${p.unit}" else ""}")
                        Slider(value = v, onValueChange = { params[p.id] = it }, valueRange = p.min..p.max)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { onApply(cur.id, params.toMap()); onDismiss() }) { Text(cur.primaryAction) } },
            dismissButton = { TextButton(onClick = { selected = null }) { Text("Back") } },
        )
    }
}
