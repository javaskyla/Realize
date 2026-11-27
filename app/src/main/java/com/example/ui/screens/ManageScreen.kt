package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.credentials.CredentialManager
import com.example.data.model.Barber
import com.example.data.model.ServiceItem
import com.example.data.model.ShopMember
import com.example.data.model.StaffAccount
import com.example.data.model.StaffRole
import com.example.ui.auth.signOut
import com.example.ui.components.ThermalPrinterSetupDialog
import com.example.ui.theme.EmeraldSoft
import com.example.ui.theme.EmeraldStatus
import com.example.ui.theme.MonoFontFamily
import com.example.ui.viewmodel.StudioSessionState
import com.example.util.ThermalPrinterManager
import com.example.util.formatRupiah

private enum class ManageTab(val label: String) {
    USERS("Pengguna"),
    SERVICES("Layanan"),
    STUDIO("Studio")
}

@Composable
fun ManageScreen(
    state: StudioSessionState.Active,
    activeStaff: StaffAccount,
    isCloudSyncEnabled: Boolean = false,
    isBusy: Boolean,
    onInviteStaff: (name: String, email: String, role: StaffRole, commission: Long) -> Unit,
    onRevokeInvite: (inviteId: String) -> Unit,
    onUpdateMember: (uid: String, name: String, role: StaffRole, commission: Long, active: Boolean) -> Unit,
    onSaveStaffAccount: (existingId: String?, username: String, rawPassword: String?, displayName: String, role: StaffRole, commission: Long, active: Boolean) -> Unit,
    onDeleteStaffAccount: (account: StaffAccount) -> Unit,
    onSaveBarber: (existingId: String?, name: String, specialty: String, commission: Long, active: Boolean) -> Unit,
    onSaveService: (existingId: String?, name: String, category: String, price: Long, duration: Long, active: Boolean) -> Unit,
    onUpdateShopProfile: (name: String, address: String, phone: String) -> Unit,
    onSyncAccess: () -> Unit,
    onLogoutStaff: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    var selectedTab by rememberSaveable { mutableStateOf(ManageTab.USERS) }
    var showInviteDialog by remember { mutableStateOf(false) }
    var editingMember by remember { mutableStateOf<ShopMember?>(null) }
    var showAddStaffDialog by remember { mutableStateOf(false) }
    var editingStaffAccount by remember { mutableStateOf<StaffAccount?>(null) }
    var deletingStaffAccount by remember { mutableStateOf<StaffAccount?>(null) }
    var editingBarber by remember { mutableStateOf<Barber?>(null) }
    var showAddBarberDialog by remember { mutableStateOf(false) }
    var editingService by remember { mutableStateOf<ServiceItem?>(null) }
    var showAddServiceDialog by remember { mutableStateOf(false) }
    var showPrinterSetupDialog by remember { mutableStateOf(false) }
    var printerConfig by remember { mutableStateOf(ThermalPrinterManager.loadConfig(context)) }

    val role = activeStaff.staffRole
    val isOwner = role == StaffRole.OWNER

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Kelola Studio & Pengguna", style = MaterialTheme.typography.headlineLarge)
        }

        item {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ManageTab.entries.forEachIndexed { index, tab ->
                    SegmentedButton(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = ManageTab.entries.size),
                        modifier = Modifier.testTag("manage_tab_${tab.name}")
                    ) {
                        Text(tab.label)
                    }
                }
            }
        }

        when (selectedTab) {
            ManageTab.USERS -> {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Akun Login Staf (${state.staffAccounts.size})", style = MaterialTheme.typography.titleMedium)
                        if (isOwner) {
                            Button(
                                onClick = { showAddStaffDialog = true },
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                modifier = Modifier.testTag("add_staff_account_button")
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Tambah User")
                            }
                        }
                    }
                }

                items(state.staffAccounts, key = { "staff_${it.id}" }) { account ->
                    val isCurrentStaff = account.id == activeStaff.id
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("staff_account_card_${account.username}")
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(account.displayName, style = MaterialTheme.typography.titleSmall)
                                    Surface(
                                        color = if (account.active) EmeraldSoft else MaterialTheme.colorScheme.errorContainer,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = if (account.active) account.staffRole.label else "${account.staffRole.label} • Nonaktif",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (account.active) EmeraldStatus else MaterialTheme.colorScheme.error,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    if (isCurrentStaff) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = "Aktif",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = "Username: ${account.username}" + if (account.commissionRate > 0L) " • Komisi ${account.commissionRate}%" else "",
                                    fontFamily = MonoFontFamily,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (isOwner) {
                                Row {
                                    IconButton(
                                        onClick = { editingStaffAccount = account },
                                        modifier = Modifier.testTag("edit_staff_account_${account.username}")
                                    ) {
                                        Icon(Icons.Rounded.Edit, contentDescription = "Ubah user login")
                                    }
                                    if (!isCurrentStaff) {
                                        IconButton(
                                            onClick = { deletingStaffAccount = account },
                                            modifier = Modifier.testTag("delete_staff_account_${account.username}")
                                        ) {
                                            Icon(
                                                Icons.Rounded.DeleteOutline,
                                                contentDescription = "Hapus user login",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (isCloudSyncEnabled) {
                    item {
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Akses Perangkat Google (${state.members.size})", style = MaterialTheme.typography.titleMedium)
                            if (isOwner) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    IconButton(
                                        onClick = onSyncAccess,
                                        modifier = Modifier.testTag("sync_members_button")
                                    ) {
                                        Icon(Icons.Rounded.Sync, contentDescription = "Sinkronkan akses staf")
                                    }
                                    OutlinedButton(
                                        onClick = { showInviteDialog = true },
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                        modifier = Modifier.testTag("invite_staff_button")
                                    ) {
                                        Icon(Icons.Rounded.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Undang")
                                    }
                                }
                            }
                        }
                    }

                    items(state.members, key = { "member_${it.id}" }) { member ->
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(member.displayName, style = MaterialTheme.typography.titleSmall)
                                        Surface(
                                            color = if (member.active) EmeraldSoft else MaterialTheme.colorScheme.errorContainer,
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = member.staffRole.label,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (member.active) EmeraldStatus else MaterialTheme.colorScheme.error,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = member.email,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (member.commissionRate > 0L) {
                                        Text(
                                            text = "Komisi: ${member.commissionRate}%",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                if (isOwner && member.uid != state.shop.ownerUid) {
                                    IconButton(onClick = { editingMember = member }) {
                                        Icon(Icons.Rounded.Edit, contentDescription = "Ubah pengguna")
                                    }
                                }
                            }
                        }
                    }

                    if (isOwner && state.invites.isNotEmpty()) {
                        item {
                            Text(
                                text = "Undangan Staf (${state.invites.size})",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        items(state.invites, key = { it.id }) { invite ->
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${invite.staffName} (${invite.staffRole.label})", style = MaterialTheme.typography.titleSmall)
                                        Text("${invite.email} • Status: ${invite.status}", style = MaterialTheme.typography.bodySmall)
                                    }
                                    IconButton(onClick = { onRevokeInvite(invite.id) }) {
                                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Hapus undangan")
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Kursi Kapster (${state.barbers.size})", style = MaterialTheme.typography.titleMedium)
                        if (isOwner) {
                            OutlinedButton(
                                onClick = { showAddBarberDialog = true },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("add_barber_button")
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Kapster")
                            }
                        }
                    }
                }

                items(state.barbers, key = { it.id }) { barber ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (barber.active) barber.name else "${barber.name} (Nonaktif)",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "${barber.specialty.ifBlank { "Stylist" }} • Komisi ${barber.commissionRate}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (isOwner) {
                                IconButton(onClick = { editingBarber = barber }) {
                                    Icon(Icons.Rounded.Edit, contentDescription = "Edit kapster")
                                }
                            }
                        }
                    }
                }
            }

            ManageTab.SERVICES -> {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Daftar Layanan (${state.services.size})", style = MaterialTheme.typography.titleMedium)
                        if (isOwner) {
                            Button(
                                onClick = { showAddServiceDialog = true },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("add_service_button")
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Layanan")
                            }
                        }
                    }
                }

                items(state.services, key = { it.id }) { service ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (service.active) service.name else "${service.name} (Nonaktif)",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "${service.category} • ~${service.durationMinutes} mnt",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = formatRupiah(service.price),
                                    fontFamily = MonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (isOwner) {
                                    IconButton(onClick = { editingService = service }) {
                                        Icon(Icons.Rounded.Edit, contentDescription = "Edit layanan")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            ManageTab.STUDIO -> {
                item {
                    ShopProfileEditorCard(
                        initialName = state.shop.name,
                        initialAddress = state.shop.address,
                        initialPhone = state.shop.phone,
                        isOwner = isOwner,
                        isBusy = isBusy,
                        onSave = onUpdateShopProfile
                    )
                }

                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Bluetooth,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text("Printer Thermal Bluetooth", style = MaterialTheme.typography.titleMedium)
                            }
                            Text(
                                text = if (printerConfig.hasSavedPrinter) {
                                    "Printer Default: ${printerConfig.printerName.ifBlank { printerConfig.printerAddress }} (${printerConfig.paperWidth.label})"
                                } else {
                                    "Belum ada printer thermal default yang dipilih."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedButton(
                                onClick = { showPrinterSetupDialog = true },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("configure_thermal_printer_button")
                            ) {
                                Icon(Icons.Rounded.Bluetooth, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Atur Printer Thermal & Tes Cetak")
                            }
                        }
                    }
                }

                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("Akun Aktif", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = activeStaff.displayName,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "Username: ${activeStaff.username} • Peran: ${role.label}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (isCloudSyncEnabled && !state.currentMember?.email.isNullOrBlank()) {
                                Text(
                                    text = "Cloud Google: ${state.currentMember?.email.orEmpty()}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            OutlinedButton(
                                onClick = onLogoutStaff,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("lock_staff_session_button")
                            ) {
                                Icon(Icons.Rounded.Lock, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Kunci / Ganti User (Form Login)")
                            }
                            if (isCloudSyncEnabled) {
                                Button(
                                    onClick = { signOut(credentialManager, scope) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                                    ),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("sign_out_button")
                                ) {
                                    Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Keluar dari Akun Google")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPrinterSetupDialog) {
        ThermalPrinterSetupDialog(
            shop = state.shop,
            ticket = null,
            onConfigSaved = { updated -> printerConfig = updated },
            onDismiss = { showPrinterSetupDialog = false }
        )
    }

    if (showAddStaffDialog || editingStaffAccount != null) {
        StaffAccountEditorDialog(
            existing = editingStaffAccount,
            isBusy = isBusy,
            onDismiss = {
                showAddStaffDialog = false
                editingStaffAccount = null
            },
            onSave = { username, rawPassword, displayName, staffRole, commission, active ->
                onSaveStaffAccount(editingStaffAccount?.id, username, rawPassword, displayName, staffRole, commission, active)
                showAddStaffDialog = false
                editingStaffAccount = null
            }
        )
    }

    deletingStaffAccount?.let { target ->
        AlertDialog(
            onDismissRequest = { deletingStaffAccount = null },
            title = { Text("Hapus User ${target.username}?") },
            text = { Text("Akun login staf ${target.displayName} (${target.staffRole.label}) akan dihapus dan tidak dapat digunakan lagi.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteStaffAccount(target)
                        deletingStaffAccount = null
                    }
                ) {
                    Text("Hapus", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingStaffAccount = null }) {
                    Text("Batal")
                }
            }
        )
    }

    if (showInviteDialog) {
        InviteStaffDialog(
            isBusy = isBusy,
            onDismiss = { showInviteDialog = false },
            onInvite = { name, email, staffRole, comm ->
                onInviteStaff(name, email, staffRole, comm)
                showInviteDialog = false
            }
        )
    }

    editingMember?.let { member ->
        EditMemberDialog(
            member = member,
            onDismiss = { editingMember = null },
            onSave = { name, staffRole, comm, active ->
                onUpdateMember(member.uid, name, staffRole, comm, active)
                editingMember = null
            }
        )
    }

    if (showAddBarberDialog || editingBarber != null) {
        BarberEditorDialog(
            existing = editingBarber,
            isBusy = isBusy,
            onDismiss = {
                showAddBarberDialog = false
                editingBarber = null
            },
            onSave = { name, spec, comm, active ->
                onSaveBarber(editingBarber?.id, name, spec, comm, active)
                showAddBarberDialog = false
                editingBarber = null
            }
        )
    }

    if (showAddServiceDialog || editingService != null) {
        ServiceEditorDialog(
            existing = editingService,
            isBusy = isBusy,
            onDismiss = {
                showAddServiceDialog = false
                editingService = null
            },
            onSave = { name, cat, price, dur, active ->
                onSaveService(editingService?.id, name, cat, price, dur, active)
                showAddServiceDialog = false
                editingService = null
            }
        )
    }
}

@Composable
private fun ShopProfileEditorCard(
    initialName: String,
    initialAddress: String,
    initialPhone: String,
    isOwner: Boolean,
    isBusy: Boolean,
    onSave: (String, String, String) -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var address by remember(initialAddress) { mutableStateOf(initialAddress) }
    var phone by remember(initialPhone) { mutableStateOf(initialPhone) }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Identitas Studio (Header Struk)", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                enabled = isOwner,
                label = { Text("Nama Barbershop") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                enabled = isOwner,
                label = { Text("Alamat") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                enabled = isOwner,
                label = { Text("No. Telepon / WhatsApp") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (isOwner) {
                Button(
                    onClick = { onSave(name, address, phone) },
                    enabled = !isBusy && name.isNotBlank(),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Simpan Profil Studio")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InviteStaffDialog(
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onInvite: (name: String, email: String, role: StaffRole, commission: Long) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(StaffRole.KASIR) }
    var commText by remember { mutableStateOf("40") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Undang Staf Baru", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Masukkan email Google staf agar mereka bisa masuk dari perangkat masing-masing.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nama Staf *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("invite_name_input")
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Google *") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth().testTag("invite_email_input")
                )
                Text("Peran", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StaffRole.entries.forEach { r ->
                        FilterChip(
                            selected = role == r,
                            onClick = { role = r },
                            label = { Text(r.label) }
                        )
                    }
                }
                if (role == StaffRole.KAPSTER) {
                    OutlinedTextField(
                        value = commText,
                        onValueChange = { commText = it.filter { c -> c.isDigit() } },
                        label = { Text("Komisi (%)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Batal") }
                    Button(
                        onClick = {
                            val comm = if (role == StaffRole.KAPSTER) (commText.toLongOrNull() ?: 40L).coerceIn(0L, 100L) else 0L
                            onInvite(name, email, role, comm)
                        },
                        enabled = !isBusy && name.isNotBlank() && email.contains("@"),
                        modifier = Modifier.weight(1f).testTag("send_invite_button")
                    ) {
                        Text("Kirim")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditMemberDialog(
    member: ShopMember,
    onDismiss: () -> Unit,
    onSave: (name: String, role: StaffRole, commission: Long, active: Boolean) -> Unit
) {
    var name by remember { mutableStateOf(member.displayName) }
    var role by remember { mutableStateOf(member.staffRole) }
    var commText by remember { mutableStateOf(member.commissionRate.toString()) }
    var active by remember { mutableStateOf(member.active) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Kelola Pengguna", style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nama") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StaffRole.entries.forEach { r ->
                        FilterChip(selected = role == r, onClick = { role = r }, label = { Text(r.label) })
                    }
                }
                OutlinedTextField(
                    value = commText,
                    onValueChange = { commText = it.filter { c -> c.isDigit() } },
                    label = { Text("Komisi (%)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Akses Aktif", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = active, onCheckedChange = { active = it })
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Batal") }
                    Button(
                        onClick = { onSave(name, role, (commText.toLongOrNull() ?: 0L).coerceIn(0L, 100L), active) },
                        enabled = name.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) { Text("Simpan") }
                }
            }
        }
    }
}

@Composable
private fun BarberEditorDialog(
    existing: Barber?,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, specialty: String, commission: Long, active: Boolean) -> Unit
) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var specialty by remember { mutableStateOf(existing?.specialty.orEmpty()) }
    var commText by remember { mutableStateOf((existing?.commissionRate ?: 40L).toString()) }
    var active by remember { mutableStateOf(existing?.active ?: true) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (existing == null) "Tambah Kapster" else "Edit Kapster", style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nama Kapster *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = specialty,
                    onValueChange = { specialty = it },
                    label = { Text("Keahlian (mis. Fade & Beard)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = commText,
                    onValueChange = { commText = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text("Komisi (%)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Kapster Aktif", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = active, onCheckedChange = { active = it })
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Batal") }
                    Button(
                        onClick = { onSave(name, specialty, (commText.toLongOrNull() ?: 40L).coerceIn(0L, 100L), active) },
                        enabled = !isBusy && name.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) { Text("Simpan") }
                }
            }
        }
    }
}

@Composable
private fun ServiceEditorDialog(
    existing: ServiceItem?,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, category: String, price: Long, duration: Long, active: Boolean) -> Unit
) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var category by remember { mutableStateOf(existing?.category ?: "Haircut") }
    var priceText by remember { mutableStateOf((existing?.price ?: 45000L).toString()) }
    var durationText by remember { mutableStateOf((existing?.durationMinutes ?: 30L).toString()) }
    var active by remember { mutableStateOf(existing?.active ?: true) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (existing == null) "Tambah Layanan" else "Edit Layanan", style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nama Layanan *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Kategori") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it.filter { c -> c.isDigit() }.take(12) },
                    label = { Text("Harga (Rp) *") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = durationText,
                    onValueChange = { durationText = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("Estimasi Durasi (Menit)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Layanan Aktif", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = active, onCheckedChange = { active = it })
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Batal") }
                    Button(
                        onClick = {
                            onSave(
                                name,
                                category,
                                priceText.toLongOrNull() ?: 0L,
                                (durationText.toLongOrNull() ?: 30L).coerceIn(5L, 480L),
                                active
                            )
                        },
                        enabled = !isBusy && name.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) { Text("Simpan") }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StaffAccountEditorDialog(
    existing: StaffAccount?,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSave: (username: String, rawPassword: String?, displayName: String, role: StaffRole, commission: Long, active: Boolean) -> Unit
) {
    var username by remember { mutableStateOf(existing?.username.orEmpty()) }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var displayName by remember { mutableStateOf(existing?.displayName.orEmpty()) }
    var role by remember { mutableStateOf(existing?.staffRole ?: StaffRole.KASIR) }
    var commText by remember { mutableStateOf((existing?.commissionRate ?: if (role == StaffRole.KAPSTER) 40L else 0L).toString()) }
    var active by remember { mutableStateOf(existing?.active ?: true) }

    val passwordValid = existing != null || password.isNotBlank()

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (existing == null) "Tambah User Login" else "Edit User Login",
                    style = MaterialTheme.typography.titleLarge
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username *") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("staff_account_username_input")
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(if (existing == null) "Password *" else "Password Baru (Opsional)") },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = if (showPassword) "Sembunyikan password" else "Tampilkan password"
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("staff_account_password_input")
                )
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Nama Lengkap *") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("staff_account_name_input")
                )
                Text("Peran", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StaffRole.entries.forEach { r ->
                        FilterChip(
                            selected = role == r,
                            onClick = {
                                role = r
                                if (r == StaffRole.KAPSTER && (commText.isBlank() || commText == "0")) {
                                    commText = "40"
                                }
                            },
                            label = { Text(r.label) },
                            modifier = Modifier.testTag("staff_role_chip_${r.name}")
                        )
                    }
                }
                if (role == StaffRole.KAPSTER) {
                    OutlinedTextField(
                        value = commText,
                        onValueChange = { commText = it.filter { c -> c.isDigit() }.take(3) },
                        label = { Text("Komisi (%)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Akun Aktif", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = active, onCheckedChange = { active = it })
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Batal") }
                    Button(
                        onClick = {
                            val comm = if (role == StaffRole.KAPSTER) (commText.toLongOrNull() ?: 40L).coerceIn(0L, 100L) else 0L
                            onSave(username, password.ifBlank { null }, displayName, role, comm, active)
                        },
                        enabled = !isBusy && username.isNotBlank() && displayName.isNotBlank() && passwordValid,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("save_staff_account_button")
                    ) { Text("Simpan") }
                }
            }
        }
    }
}

