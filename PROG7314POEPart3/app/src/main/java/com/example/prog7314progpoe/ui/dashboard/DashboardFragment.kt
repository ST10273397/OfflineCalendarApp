package com.example.prog7314progpoe.ui.dashboard

// -------------------------
// Android UI imports
// -------------------------
import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.view.*
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.prog7314progpoe.R
import androidx.navigation.fragment.findNavController

// -------------------------
// Offline / session
// -------------------------
import com.example.prog7314progpoe.offline.SessionManager
import com.example.prog7314progpoe.offline.OfflineManager

// -------------------------
// API / repo / models
// -------------------------
import com.example.prog7314progpoe.api.*
import com.example.prog7314progpoe.database.calendar.CalendarModel
import com.example.prog7314progpoe.database.calendar.CustomCalendarRepository
import com.example.prog7314progpoe.database.calendar.FirebaseCalendarDbHelper
import com.example.prog7314progpoe.ui.dashboard.SlotUiModel

// -------------------------
// Time & coroutines
// -------------------------
import kotlinx.coroutines.*
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * DashboardFragment
 *
 * Shows 8 slots representing either:
 * - Public holiday calendars (by country)
 * - Custom user calendars (from Firebase)
 *
 * Supports offline caching via OfflineManager and user-specific slots via SharedPreferences.
 */
class DashboardFragment : Fragment() {

    // -------------------------
    // Constants / Enums
    // -------------------------
    private enum class CalType { PUBLIC, CUSTOM }
    private data class SlotAssignment(val type: CalType, val id: String, val displayName: String)
    private data class CalRow(val id: String, val title: String)

    // -------------------------
    // Time / Clock
    // -------------------------
    @RequiresApi(Build.VERSION_CODES.O)
    private val userZone: ZoneId = ZoneId.systemDefault()

    private lateinit var timeText: TextView
    private val timeHandler = Handler(Looper.getMainLooper())
    private val timeUpdater = object : Runnable {
        @RequiresApi(Build.VERSION_CODES.O)
        override fun run() {
            updateTimeNow()
            timeHandler.postDelayed(this, 60_000L)
        }
    }

    // -------------------------
    // UI Elements
    // -------------------------
    private lateinit var recycler: RecyclerView
    private lateinit var swipe: SwipeRefreshLayout
    private lateinit var adapter: DashboardSlotsAdapter

    // -------------------------
    // Coroutine scope
    // -------------------------
    private val uiScope = CoroutineScope(Dispatchers.Main + Job())

    // -------------------------
    // Data sources
    // -------------------------
    private var countries: List<Country> = emptyList()
    private val repo by lazy { HolidayRepository(requireContext()) }
    private val customCalRepo by lazy { CustomCalendarRepository(requireContext()) }
    private lateinit var offlineManager: OfflineManager
    private lateinit var sessionManager: SessionManager

    // -------------------------
    // SharedPreferences for per-user slot storage
    // -------------------------
    private fun userPrefs(): SharedPreferences {
        val uid = sessionManager.getCurrentUserId() ?: "guest"
        return requireContext().getSharedPreferences("dashboard_slots_$uid", Context.MODE_PRIVATE)
    }

    private fun saveSlot(index: Int, slot: SlotAssignment?) {
        val editor = userPrefs().edit()
        if (slot == null) {
            editor.remove("type_$index")
            editor.remove("id_$index")
            editor.remove("name_$index")
        } else {
            editor.putString("type_$index", slot.type.name)
            editor.putString("id_$index", slot.id)
            editor.putString("name_$index", slot.displayName)
        }
        editor.apply()
    }

    private fun loadSlot(index: Int): SlotAssignment? {
        val prefs = userPrefs()
        val type = prefs.getString("type_$index", null) ?: return null
        val id = prefs.getString("id_$index", null) ?: return null
        val name = prefs.getString("name_$index", null) ?: return null
        return SlotAssignment(CalType.valueOf(type), id, name)
    }

    // -------------------------
    // Lifecycle
    // -------------------------
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_dashboard, container, false)
        timeText = view.findViewById(R.id.txtTime)
        recycler = view.findViewById(R.id.recyclerSlots)
        swipe = view.findViewById(R.id.swipe)
        offlineManager = OfflineManager(requireContext())
        sessionManager = SessionManager(requireContext())
        return view
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupRecyclerView()
        setupClock()
        loadDashboardData()
        swipe.setOnRefreshListener { refreshAllSlots() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        timeHandler.removeCallbacks(timeUpdater)
    }

    // -------------------------
    // RecyclerView / Grid
    // -------------------------
    @RequiresApi(Build.VERSION_CODES.O)
    private fun setupRecyclerView() {
        adapter = DashboardSlotsAdapter { index -> onSlotClicked(index) }
        recycler.layoutManager = GridLayoutManager(requireContext(), 2)
        recycler.adapter = adapter
        recycler.addItemDecoration(GridSpacingDecoration(dpToPx(8)))
        recycler.setHasFixedSize(true)
    }

    // -------------------------
    // Clock handling
    // -------------------------
    @RequiresApi(Build.VERSION_CODES.O)
    private fun setupClock() {
        updateTimeNow()
        timeHandler.removeCallbacks(timeUpdater)
        timeHandler.post(timeUpdater)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun updateTimeNow() {
        val now = ZonedDateTime.now(userZone)
        timeText.text = now.format(DateTimeFormatter.ofPattern("HH:mm — EEE, dd MMM"))
    }

    // -------------------------
    // Dashboard data loading
    // -------------------------
    @RequiresApi(Build.VERSION_CODES.O)
    private fun loadDashboardData()
    {
        uiScope.launch{
            val userId = sessionManager.getCurrentUserId()
            if (userId == null)
            {
                Toast.makeText(requireContext(), "No user logged in", Toast.LENGTH_SHORT)
                    .show()
                return@launch
            }
            if (offlineManager.isOnline())
            {
                offlineManager.syncDashboardToOffline(userId)
                {
                    syncSuccess ->
                    if (syncSuccess)
                    {
                        uiScope.launch{
                            offlineManager.syncPublicHolidaysForDashboard(userId)
                            {
                                syncResult ->
                                if (syncResult)
                                {
                                    Log.d("DashboardFragment", "Public holidays synced")
                                }
                            }
                        }
                    }
                    renderFromPrefs()
                }
            }
            else
            {
                Toast.makeText(requireContext(), "Offline mode", Toast.LENGTH_SHORT)
                    .show()
                loadOfflineCalendarsForUser(userId)
            }
        }
        view?.findViewById<View>(R.id.fabSettings)?.setOnClickListener {
            findNavController().navigate(R.id.nav_settings)
        }

        // Data
        renderFromPrefs()
        swipe.setOnRefreshListener { refreshAllSlots() }
    }

    // -------------------------
    // Offline calendar handling
    // -------------------------
    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun loadOfflineCalendarsForUser(userId: String) {
        val calendars = offlineManager.getOfflineCalendars(userId)
        if (calendars.isEmpty()) {
            Toast.makeText(requireContext(), "No offline calendars available. Connect to sync.", Toast.LENGTH_LONG).show()
            return
        }

        val list = mutableListOf<SlotUiModel>()
        calendars.take(8).forEachIndexed { index, cal ->
            list.add(SlotUiModel.Populated(index, cal.title ?: "(Untitled)", "Loading..."))
        }
        while (list.size < 8) list.add(SlotUiModel.Unassigned(list.size))
        adapter.submitList(list)

        refreshOfflineSlots(calendars)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun refreshOfflineSlots(calendars: List<CalendarModel>) {
        uiScope.launch {
            val current = adapter.currentList.toMutableList()
            val today = LocalDate.now(userZone)
            calendars.forEachIndexed { index, cal ->
                if (index >= 8) return@forEachIndexed
                val nextText = fetchNextCustomEventOffline(cal.calendarId ?: "", today)
                current[index] = SlotUiModel.Populated(index, cal.title ?: "(Untitled)", nextText)
            }
            adapter.submitList(current)
            swipe.isRefreshing = false
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun fetchNextCustomEventOffline(calendarId: String, today: LocalDate): String = withContext(Dispatchers.IO) {
        try {
            val holidays = offlineManager.getOfflineCustomHolidays(calendarId)
            val mapped = holidays.mapNotNull { h ->
                val title = h.name?.trim().orEmpty()
                val date = offlineManager.parseIsoToLocalDate(offlineManager.getHolidayCandidateIso(h)) ?: return@mapNotNull null
                title to date
            }
            val next = mapped.filter { it.second >= today }.minByOrNull { it.second }
            if (next == null) getString(R.string.no_upcoming)
            else "${next.first} — ${next.second.format(DateTimeFormatter.ofPattern("EEE, dd MMM"))}"
        } catch (e: Exception) {
            Log.e("DashboardFragment", "Error fetching custom event offline", e)
            getString(R.string.no_upcoming)
        }
    }

    // -------------------------
    // Slot handling: render, refresh, click
    // -------------------------
    @RequiresApi(Build.VERSION_CODES.O)
    private fun renderFromPrefs() {
        val list = MutableList(8) { i ->
            val slot = loadSlot(i)
            if (slot == null) SlotUiModel.Unassigned(i)
            else SlotUiModel.Populated(i, slot.displayName, "…")
        }
        adapter.submitList(list)
        refreshAllSlots()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun refreshAllSlots() {
        swipe.isRefreshing = true
        uiScope.launch {
            val current = adapter.currentList.toMutableList()
            val today = LocalDate.now(userZone)
            current.indices.forEach { i ->
                loadSlot(i)?.let { assign ->
                    val nextText = when (assign.type) {
                        CalType.PUBLIC -> fetchNextPublicHoliday(assign.id, today)
                        CalType.CUSTOM -> fetchNextCustomEvent(assign.id, today)
                    }
                    current[i] = SlotUiModel.Populated(i, assign.displayName, nextText)
                }
            }
            adapter.submitList(current)
            swipe.isRefreshing = false
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun refreshSingleSlot(index: Int) {
        uiScope.launch {
            val assign = loadSlot(index) ?: return@launch
            val today = LocalDate.now(userZone)
            val nextText = when (assign.type) {
                CalType.PUBLIC -> fetchNextPublicHoliday(assign.id, today)
                CalType.CUSTOM -> fetchNextCustomEvent(assign.id, today)
            }
            val list = adapter.currentList.toMutableList()
            list[index] = SlotUiModel.Populated(index, assign.displayName, nextText)
            adapter.submitList(list)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun onSlotClicked(index: Int) {
        val existing = loadSlot(index)
        val options = if (existing == null) {
            arrayOf(getString(R.string.picker_public_holidays), getString(R.string.picker_custom_calendar))
        } else {
            arrayOf(getString(R.string.picker_public_holidays), getString(R.string.picker_custom_calendar), "Clear")
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Choose source")
            .setItems(options) { _, which ->
                when (options[which]) {
                    getString(R.string.picker_public_holidays) -> pickCountryAndAssign(index)
                    getString(R.string.picker_custom_calendar) -> promptCustomListAndAssign(index)
                    "Clear" -> {
                        saveSlot(index, null)
                        val list = adapter.currentList.toMutableList()
                        list[index] = SlotUiModel.Unassigned(index)
                        adapter.submitList(list)
                    }
                }
            }.show()
    }

    // -------------------------
    // Public holiday logic
    // -------------------------
    @RequiresApi(Build.VERSION_CODES.O)
    private fun pickCountryAndAssign(index: Int) {
        if (countries.isEmpty()) {
            ApiClient.api.getLocations(ApiConfig.apiKey()).enqueue(object : retrofit2.Callback<CountryResponse> {
                override fun onResponse(call: retrofit2.Call<CountryResponse>, response: retrofit2.Response<CountryResponse>) {
                    if (response.isSuccessful) {
                        countries = response.body()?.response?.countries ?: emptyList()
                        showCountryDialog(index)
                    } else Toast.makeText(requireContext(), "Failed to load countries", Toast.LENGTH_SHORT).show()
                }
                override fun onFailure(call: retrofit2.Call<CountryResponse>, t: Throwable) {
                    Toast.makeText(requireContext(), "Network error loading countries", Toast.LENGTH_SHORT).show()
                }
            })
        } else showCountryDialog(index)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun showCountryDialog(index: Int) {
        if (countries.isEmpty()) {
            Toast.makeText(requireContext(), "No countries available", Toast.LENGTH_SHORT).show()
            return
        }
        val names = countries.map { it.country_name }
        showSearchableListDialog(title = "Pick country", items = names, emptyHint = "No countries available") { chosenName ->
            val pos = names.indexOf(chosenName ?: return@showSearchableListDialog)
            val country = countries[pos]
            val assign = SlotAssignment(CalType.PUBLIC, country.isoCode, country.country_name)
            saveSlot(index, assign)
            val list = adapter.currentList.toMutableList()
            list[index] = SlotUiModel.Populated(index, assign.displayName, "…")
            adapter.submitList(list)
            refreshSingleSlot(index)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun fetchNextPublicHoliday(countryIso: String, today: LocalDate): String = withContext(Dispatchers.IO) {
        val fmt = DateTimeFormatter.ofPattern("EEE, dd MMM")
        val years = listOf(today.year, today.year + 1)
        val all = mutableListOf<Pair<String, LocalDate>>()

        try {
            years.forEach { year ->
                val holidays = if (offlineManager.isOnline()) {
                    val resp = repo.getHolidays(countryIso, year)
                    val list = resp.response?.holidays ?: emptyList()
                    offlineManager.savePublicHolidaysOffline(countryIso, year, list)
                    list
                } else offlineManager.getOfflinePublicHolidays(countryIso, year)

                holidays.forEach { h ->
                    val date = offlineManager.parseIsoToLocalDate(offlineManager.getHolidayCandidateIso(h)) ?: return@forEach
                    all += (h.name ?: "(Untitled)") to date
                }
            }

            val next = all.filter { it.second >= today }.minByOrNull { it.second }
            if (next == null) getString(R.string.no_upcoming)
            else "${next.first} — ${next.second.format(fmt)}"
        } catch (e: Exception) {
            Log.e("DashboardFragment", "Error fetching public holiday", e)
            "Error loading"
        }
    }

    // -------------------------
    // Custom calendar logic
    // -------------------------
    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun fetchNextCustomEvent(calendarId: String, today: LocalDate): String = withContext(Dispatchers.IO) {
        try {
            val holidays = if (offlineManager.isOnline()) customCalRepo.getHolidaysForCalendar(calendarId, true)
            else offlineManager.getOfflineCustomHolidays(calendarId)

            val mapped = holidays.mapNotNull { h ->
                val title = h.name ?: ""
                val candidateIso = offlineManager.getHolidayCandidateIso(h) ?: ""
                val date = offlineManager.parseIsoToLocalDate(candidateIso) ?: return@mapNotNull null
                title to date
            }

            val next = mapped.filter { it.second >= today }.minByOrNull { it.second }
            if (next == null) getString(R.string.no_upcoming)
            else "${next.first} — ${next.second.format(DateTimeFormatter.ofPattern("EEE, dd MMM"))}"
        } catch (e: Exception) {
            Log.e("DashboardFragment", "Error fetching custom event", e)
            "Error loading"
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun promptCustomListAndAssign(index: Int) {
        val uid = sessionManager.getCurrentUserId() ?: run {
            Toast.makeText(requireContext(), "Not signed in", Toast.LENGTH_SHORT).show()
            return
        }

        if (!offlineManager.isOnline()) {
            uiScope.launch {
                val cached = offlineManager.getOfflineCalendars(uid)
                if (cached.isEmpty()) {
                    Toast.makeText(requireContext(), "No custom calendars offline", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                showCustomCalendarDialog(index, cached.map { CalRow(it.calendarId!!, it.title ?: "(Untitled)") })
            }
            return
        }

        // Online: Firebase fetch
        FirebaseCalendarDbHelper.getCalendarsForUser(uid) { list ->
            if (list.isEmpty()) {
                Toast.makeText(requireContext(), "No custom calendars yet", Toast.LENGTH_SHORT).show()
                return@getCalendarsForUser
            }
            showCustomCalendarDialog(index, list.map { CalRow(it.calendarId!!, it.title ?: "(Untitled)") })
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun showCustomCalendarDialog(index: Int, rows: List<CalRow>) {
        val names = rows.sortedBy { it.title.lowercase() }.map { it.title }
        showSearchableListDialog("Choose custom calendar", names, "No custom calendars yet") { chosen ->
            val pos = names.indexOf(chosen ?: return@showSearchableListDialog)
            val row = rows[pos]
            val assign = SlotAssignment(CalType.CUSTOM, row.id, row.title)
            saveSlot(index, assign)
            val listNow = adapter.currentList.toMutableList()
            listNow[index] = SlotUiModel.Populated(index, assign.displayName, "…")
            adapter.submitList(listNow)
            refreshSingleSlot(index)
        }
    }

    // -------------------------
    // Searchable List Dialog
    // -------------------------
    private fun showSearchableListDialog(title: String, items: List<String>, emptyHint: String, onChoose: (String?) -> Unit) {
        val ctx = requireContext()
        val container = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8)) }
        val search = EditText(ctx).apply { hint = "Search…"; inputType = InputType.TYPE_CLASS_TEXT }
        val listView = ListView(ctx)
        val adapterLV = ArrayAdapter(ctx, android.R.layout.simple_list_item_1, items.toMutableList())
        listView.adapter = adapterLV
        container.addView(search)
        container.addView(listView)
        search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                val filtered = items.filter { it.contains(s.toString(), ignoreCase = true) }
                adapterLV.clear()
                adapterLV.addAll(filtered)
                adapterLV.notifyDataSetChanged()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        val dlg = AlertDialog.Builder(ctx)
            .setTitle(title)
            .setView(container)
            .setNegativeButton("Cancel") { d, _ -> d.dismiss() }
            .create()
        listView.setOnItemClickListener { _, _, pos, _ ->
            val chosen = adapterLV.getItem(pos)
            onChoose(chosen)
            dlg.dismiss()
        }
        dlg.show()
    }

    // -------------------------
    // Helpers
    // -------------------------
    private fun dpToPx(dp: Int) = (dp * resources.displayMetrics.density + 0.5f).toInt()
    private class GridSpacingDecoration(private val space: Int) : RecyclerView.ItemDecoration() {
        override fun getItemOffsets(outRect: android.graphics.Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
            outRect.set(space, space, space, space)
        }
    }
}
