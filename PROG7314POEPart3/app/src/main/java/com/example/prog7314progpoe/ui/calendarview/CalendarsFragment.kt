package com.example.prog7314progpoe.ui.calendarview

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.prog7314progpoe.R
import com.example.prog7314progpoe.api.ApiClient
import com.example.prog7314progpoe.api.ApiConfig
import com.example.prog7314progpoe.api.Country
import com.example.prog7314progpoe.api.CountryResponse
import com.example.prog7314progpoe.api.HolidayRepository
import com.example.prog7314progpoe.database.calendar.CustomCalendarRepository
import com.example.prog7314progpoe.database.calendar.FirebaseCalendarDbHelper
import com.example.prog7314progpoe.database.holidays.HolidayModel
import com.example.prog7314progpoe.offline.OfflineManager
import com.example.prog7314progpoe.offline.SessionManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * CalendarsFragment
 *
 * Monthly calendar grid that can display events from two sources:
 *  - public country holidays (public calendars)
 *  - user-created custom calendars (custom)
 *
 * This cleaned-up fragment adds comments, safer checks, and small refactors
 * while keeping your original architecture.
 */
class CalendarsFragment : Fragment() {

    companion object {
        private const val TAG = "CalendarsFragment"
    }

    // ---------- STATE ----------
    @RequiresApi(Build.VERSION_CODES.O)
    private var currentMonth: YearMonth = YearMonth.now()
    @RequiresApi(Build.VERSION_CODES.O)
    private var selectedDay: LocalDate = LocalDate.now()

    // Selected sources
    private val activeCountryIsos = mutableSetOf<String>()
    private val activeCustomIds = mutableSetOf<String>()
    private val customIdToName = mutableMapOf<String, String>()

    // Cached data
    private var countries: List<Country> = emptyList()
    private val monthEvents = mutableMapOf<LocalDate, MutableList<EventItem>>()

    // ---------- UI ----------
    private lateinit var txtMonth: TextView
    private lateinit var btnPrev: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var btnPickSingle: Button
    private lateinit var btnPickFromDashboard: Button
    private lateinit var grid: RecyclerView
    private lateinit var dayCard: LinearLayout
    private lateinit var dayCardTitle: TextView
    private lateinit var dayCardList: LinearLayout
    private lateinit var emptyDayText: TextView

    // ---------- COROUTINES ----------
    private val uiScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // ---------- PER-USER PREFS ----------
    private lateinit var sessionManager: SessionManager
    private fun userPrefs() = requireContext().getSharedPreferences("dashboard_slots_${sessionManager.getCurrentUserId() ?: "guest"}", Context.MODE_PRIVATE)

    // ---------- REPOS ----------
    private val repo by lazy { HolidayRepository(requireContext()) }
    private val customCalRepo by lazy { CustomCalendarRepository(requireContext()) }
    private lateinit var offlineManager: OfflineManager

    // ---------- MODELS ----------
    private data class EventItem(
        val date: LocalDate,
        val title: String,
        val sourceLabel: String,
        val sourceKind: SourceKind
    )

    private enum class SourceKind { PUBLIC, CUSTOM }

    // ---------- LIFECYCLE ----------
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_calendars_month, container, false)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Bind views
        txtMonth = view.findViewById(R.id.txtMonth)
        btnPrev = view.findViewById(R.id.btnPrev)
        btnNext = view.findViewById(R.id.btnNext)
        btnPickSingle = view.findViewById(R.id.btnPickSingle)
        btnPickFromDashboard = view.findViewById(R.id.btnPickFromDashboard)
        grid = view.findViewById(R.id.calendarGrid)
        dayCard = view.findViewById(R.id.dayCard)
        dayCardTitle = view.findViewById(R.id.dayCardTitle)
        dayCardList = view.findViewById(R.id.dayCardList)
        emptyDayText = view.findViewById(R.id.emptyDayText)

        // Helpers
        sessionManager = SessionManager(requireContext())
        offlineManager = OfflineManager(requireContext())

        // Keep the day card above bottom navigation (system inset aware)
        val root = view as ViewGroup
        val bottomNav = requireActivity().findViewById<BottomNavigationView>(R.id.bottom_nav)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val sysBottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            val navH = bottomNav?.height ?: 0
            val extra = dp(16)
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, sysBottom + navH + extra)
            insets
        }

        // Grid setup
        grid.layoutManager = GridLayoutManager(requireContext(), 7)
        grid.adapter = DaysAdapter { clicked -> onDayClicked(clicked) }
        grid.addItemDecoration(EqualGapDecoration(dp(4)))
        grid.setHasFixedSize(true)

        // Month navigation
        btnPrev.setOnClickListener {
            currentMonth = currentMonth.minusMonths(1)
            refreshMonth()
        }
        btnNext.setOnClickListener {
            currentMonth = currentMonth.plusMonths(1)
            refreshMonth()
        }

        // Pickers
        btnPickSingle.setOnClickListener { onPickSingleCalendar() }
        btnPickFromDashboard.setOnClickListener { onPickFromDashboard() }

        // Initial state
        selectedDay = LocalDate.now()
        activeCountryIsos.clear()
        activeCustomIds.clear()
        customIdToName.clear()
        refreshMonth()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        uiScope.coroutineContext.cancelChildren()
    }

    // ---------- UI INTERACTIONS ----------
    @RequiresApi(Build.VERSION_CODES.O)
    private fun onDayClicked(day: LocalDate) {
        selectedDay = day
        renderDayCard()
        grid.adapter?.notifyDataSetChanged()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun refreshMonth() {
        txtMonth.text = currentMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
        renderGrid()
        fetchMonthEvents()
        renderDayCard()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun renderGrid() {
        val firstOfMonth = currentMonth.atDay(1)
        val firstDow = firstOfMonth.dayOfWeek.value % 7 // Sunday = 0
        val daysInMonth = currentMonth.lengthOfMonth()

        val cells = mutableListOf<LocalDate>()
        for (i in 0 until firstDow) cells.add(firstOfMonth.minusDays((firstDow - i).toLong()))
        for (d in 1..daysInMonth) cells.add(currentMonth.atDay(d))
        while (cells.size % 7 != 0) cells.add(cells.last().plusDays(1))

        (grid.adapter as? DaysAdapter)?.submit(cells)
    }

    // ---------- DATA LOADING ----------
    @RequiresApi(Build.VERSION_CODES.O)
    private fun fetchMonthEvents() {
        monthEvents.clear()

        val start = currentMonth.atDay(1)
        val end = currentMonth.atEndOfMonth()

        // Determine which years we may need (handles year boundary)
        val yearList = listOf(start.year, end.year).distinct()

        uiScope.launch {
            try {
                val isOnline = offlineManager.isOnline()

                if (!isOnline) {
                    Toast.makeText(requireContext(), "Offline mode - showing cached events", Toast.LENGTH_SHORT).show()
                }

                // Public holidays (per selected country ISO)
                for (iso in activeCountryIsos) {
                    val allHolidaysForCountry = mutableListOf<HolidayModel>()

                    for (y in yearList) {
                        try {
                            val holidays = if (isOnline) {
                                // Online: fetch from API and cache
                                val resp = withContext(Dispatchers.IO) { repo.getHolidays(iso, y) }
                                val hols = resp.response?.holidays ?: emptyList()
                                offlineManager.savePublicHolidaysOffline(iso, y, hols)
                                hols
                            } else {
                                // Offline: read cached public holidays for that year
                                offlineManager.getOfflinePublicHolidays(iso, y)
                            }
                            allHolidaysForCountry.addAll(holidays)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error loading holidays for $iso/$y", e)
                        }
                    }

                    // Deduplicate and map to EventItem if inside range
                    allHolidaysForCountry.distinctBy { it.holidayId }.forEach { h ->
                        // Extract candidate ISO string robustly
                        val dateIso = h.date?.iso ?: h.dateStart?.iso ?: h.dateEnd?.iso
                        if (dateIso.isNullOrBlank()) return@forEach

                        val d = runCatching { LocalDate.parse(dateIso.substring(0, 10)) }.getOrNull() ?: return@forEach

                        if (!d.isBefore(start) && !d.isAfter(end)) {
                            val title = h.name ?: "Holiday"
                            val label = resolveCountryName(iso)
                            monthEvents.getOrPut(d) { mutableListOf() }.add(EventItem(d, title, label, SourceKind.PUBLIC))
                        }
                    }
                }

                // Custom calendars
                for (cid in activeCustomIds) {
                    try {
                        val list = if (isOnline) customCalRepo.getHolidaysForCalendar(cid, forceRefresh = true)
                        else offlineManager.getOfflineCustomHolidays(cid)

                        val label = customIdToName[cid] ?: cid
                        list.forEach { h ->
                            val title = h.name ?: "Event"
                            val dateStr = h.date?.iso
                            val d = runCatching { LocalDate.parse(dateStr ?: "") }.getOrNull()
                            if (d != null && !d.isBefore(start) && !d.isAfter(end)) {
                                monthEvents.getOrPut(d) { mutableListOf() }.add(EventItem(d, title, label, SourceKind.CUSTOM))
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error loading custom calendar $cid", e)
                    }
                }

                grid.adapter?.notifyDataSetChanged()
                renderDayCard()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error loading events: ${e.message}", Toast.LENGTH_SHORT).show()
                grid.adapter?.notifyDataSetChanged()
                renderDayCard()
            }
        }
    }

    // ---------- DAY CARD UI ----------
    @RequiresApi(Build.VERSION_CODES.O)
    private fun renderDayCard() {
        val items = monthEvents[selectedDay].orEmpty()
        dayCardTitle.text = selectedDay.format(DateTimeFormatter.ofPattern("EEE, dd MMM"))
        dayCard.visibility = View.VISIBLE

        dayCardList.removeAllViews()
        if (items.isEmpty()) {
            emptyDayText.isVisible = true
        } else {
            emptyDayText.isVisible = false
            items.sortedBy { it.title }.forEach { ev -> dayCardList.addView(makeEventRow(ev)) }
        }
    }

    // ---------- PICK SINGLE CALENDAR ----------
    @RequiresApi(Build.VERSION_CODES.O)
    private fun onPickSingleCalendar() {
        // Ensure we have a country list (async network call)
        val ensureCountries = CompletableDeferred<Unit>()
        if (countries.isEmpty()) {
            ApiClient.api.getLocations(ApiConfig.apiKey()).enqueue(object : Callback<CountryResponse> {
                override fun onResponse(call: Call<CountryResponse>, response: Response<CountryResponse>) {
                    countries = response.body()?.response?.countries ?: emptyList()
                    ensureCountries.complete(Unit)
                }

                override fun onFailure(call: Call<CountryResponse>, t: Throwable) {
                    countries = emptyList()
                    ensureCountries.complete(Unit)
                }
            })
        } else ensureCountries.complete(Unit)

        // Load user's custom calendars (from cache or Firebase depending on connectivity)
        val ensureCustoms = CompletableDeferred<List<Pair<String, String>>>()
        val uid = sessionManager.getCurrentUserId()
        if (uid == null) ensureCustoms.complete(emptyList())
        else {
            if (!offlineManager.isOnline()) {
                uiScope.launch {
                    val cached = offlineManager.getOfflineCalendars(uid)
                    val rows = cached.filter { !it.calendarId.isNullOrBlank() }.map { it.calendarId!! to (it.title ?: "(Untitled)") }.sortedBy { it.second.lowercase() }
                    ensureCustoms.complete(rows)
                }
            } else {
                FirebaseCalendarDbHelper.getCalendarsForUser(uid) { list ->
                    val rows = list.filter { !it.calendarId.isNullOrBlank() }.map { it.calendarId!! to (it.title ?: "(Untitled)") }.sortedBy { it.second.lowercase() }
                    ensureCustoms.complete(rows)
                }
            }
        }

        uiScope.launch {
            ensureCountries.await()
            val customRows = ensureCustoms.await()

            // Build combined display list (public countries first, then customs)
            val display = mutableListOf<String>()
            val types = mutableListOf<SourceKind>()
            val ids = mutableListOf<String>()
            val labels = mutableListOf<String>()

            countries.forEach { c ->
                display += c.country_name
                types += SourceKind.PUBLIC
                ids += c.isoCode
                labels += c.country_name
            }
            customRows.forEach { (id, title) ->
                display += title
                types += SourceKind.CUSTOM
                ids += id
                labels += title
            }

            showSearchableListDialog(title = "Pick calendar", items = display, emptyHint = "No calendars found") { chosen ->
                if (chosen == null) return@showSearchableListDialog
                val idx = display.indexOf(chosen)
                if (idx < 0) return@showSearchableListDialog

                // Reset selections
                activeCountryIsos.clear()
                activeCustomIds.clear()
                customIdToName.clear()

                when (types[idx]) {
                    SourceKind.PUBLIC -> activeCountryIsos += ids[idx]
                    SourceKind.CUSTOM -> {
                        val id = ids[idx]
                        val name = labels[idx]
                        activeCustomIds += id
                        customIdToName[id] = name
                    }
                }
                refreshMonth()
            }
        }
    }

    // ---------- PICK FROM DASHBOARD ----------
    @RequiresApi(Build.VERSION_CODES.O)
    private fun onPickFromDashboard() {
        val slots = readDashboardSlots()
        if (slots.isEmpty()) {
            Toast.makeText(requireContext(), "No dashboard calendars to choose", Toast.LENGTH_SHORT).show()
            return
        }

        val names = slots.map { it.displayName }
        val checked = BooleanArray(slots.size) { i ->
            val s = slots[i]
            (s.kind == SourceKind.PUBLIC && activeCountryIsos.contains(s.id)) || (s.kind == SourceKind.CUSTOM && activeCustomIds.contains(s.id))
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Pick from dashboard")
            .setMultiChoiceItems(names.toTypedArray(), checked) { _, which, isChecked -> checked[which] = isChecked }
            .setPositiveButton("Apply") { d, _ ->
                activeCountryIsos.clear()
                activeCustomIds.clear()
                customIdToName.clear()

                checked.forEachIndexed { i, flag ->
                    if (flag) {
                        val s = slots[i]
                        if (s.kind == SourceKind.PUBLIC) activeCountryIsos += s.id
                        else {
                            activeCustomIds += s.id
                            customIdToName[s.id] = s.displayName
                        }
                    }
                }
                refreshMonth()
                d.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---------- HELPERS ----------
    private data class SlotRef(val id: String, val displayName: String, val kind: SourceKind)

    private fun readDashboardSlots(): List<SlotRef> {
        val p = userPrefs()
        val list = mutableListOf<SlotRef>()
        for (i in 0 until 8) {
            val type = p.getString("type_$i", null) ?: continue
            val id = p.getString("id_$i", null) ?: continue
            val name = p.getString("name_$i", null) ?: continue
            val kind = if (type == "PUBLIC") SourceKind.PUBLIC else SourceKind.CUSTOM
            list += SlotRef(id, name, kind)
        }
        return list
    }

    // Build a compact row view for a single event in the day card
    private fun makeEventRow(ev: EventItem): View {
        val ctx = requireContext()
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val tag = TextView(ctx).apply {
            text = shortTag(ev.sourceLabel)
            val tagBgId = resources.getIdentifier("bg_tag_round", "drawable", requireContext().packageName)
            if (tagBgId != 0) setBackgroundResource(tagBgId)
            setPadding(dp(8), dp(4), dp(8), dp(4))
            setTextColor(Color.parseColor("#1565C0"))
        }

        val title = TextView(ctx).apply {
            text = "  ${ev.title}"
            textSize = 16f
        }

        row.addView(tag)
        row.addView(title)
        return row
    }

    // ---------- ADAPTER ----------
    private inner class DaysAdapter(val onClick: (LocalDate) -> Unit) : RecyclerView.Adapter<DayVH>() {
        private val days = mutableListOf<LocalDate>()
        fun submit(list: List<LocalDate>) { days.clear(); days.addAll(list); notifyDataSetChanged() }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayVH { val v = LayoutInflater.from(parent.context).inflate(R.layout.item_day_cell, parent, false); return DayVH(v) }
        @RequiresApi(Build.VERSION_CODES.O)
        override fun onBindViewHolder(holder: DayVH, position: Int) { holder.bind(days[position]) }
        override fun getItemCount(): Int = days.size
    }

    private inner class DayVH(v: View) : RecyclerView.ViewHolder(v) {
        private val txtDay: TextView = v.findViewById(R.id.txtDay)
        private val txtTag: TextView = v.findViewById(R.id.txtTag)

        @RequiresApi(Build.VERSION_CODES.O)
        fun bind(day: LocalDate) {
            txtDay.text = day.dayOfMonth.toString()
            val isToday = day == LocalDate.now()
            val isSel = day == selectedDay
            itemView.isSelected = isSel
            txtDay.setTextColor(if (isToday) Color.parseColor("#1565C0") else Color.parseColor("#000000"))
            txtDay.alpha = if (day.month == currentMonth.month) 1f else 0.4f

            val evs = monthEvents[day].orEmpty()
            if (evs.isNotEmpty()) {
                val base = shortTag(evs.first().sourceLabel)
                val tagTextStr = if (evs.size > 1) "$base+" else base
                txtTag.text = tagTextStr
                txtTag.setTextColor(Color.parseColor("#1565C0"))
                txtTag.visibility = View.VISIBLE
            } else txtTag.visibility = View.GONE

            itemView.setOnClickListener { onDayClicked(day) }
        }
    }

    // ---------- UTIL ----------
    private fun shortTag(name: String): String {
        val s = name.trim()
        val letters = s.replace(Regex("[^\\p{L}]"), "")
        val base = if (letters.length >= 2) letters.substring(0, 2) else s.take(2)
        return base.uppercase()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density + 0.5f).toInt()

    private fun resolveCountryName(iso: String): String = countries.firstOrNull { it.isoCode == iso }?.country_name ?: iso

    // Reflection helpers kept for legacy/flexible parsing of API models
    private fun tryField(obj: Any, field: String): String? {
        return try {
            val f = obj.javaClass.getDeclaredField(field)
            f.isAccessible = true
            f.get(obj) as? String
        } catch (_: Exception) { null }
    }

    private fun tryDateIso(obj: Any): String? {
        return try {
            val df = obj.javaClass.getDeclaredField("date")
            df.isAccessible = true
            val v = df.get(obj)
            when (v) {
                is String -> v
                else -> {
                    val isoF = v?.javaClass?.getDeclaredField("iso")
                    isoF?.isAccessible = true
                    isoF?.get(v) as? String
                }
            }
        } catch (_: Exception) { null }
    }

    // ---------- SEARCHABLE DIALOG ----------
    private fun showSearchableListDialog(title: String, items: List<String>, emptyHint: String, onChoose: (String?) -> Unit) {
        val ctx = requireContext()
        val container = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), 0) }

        val search = EditText(ctx).apply { hint = "Search…"; inputType = InputType.TYPE_CLASS_TEXT }
        val emptyView = TextView(ctx).apply { text = emptyHint; setPadding(0, dp(24), 0, dp(24)); gravity = Gravity.CENTER; visibility = if (items.isEmpty()) View.VISIBLE else View.GONE }
        val listView = ListView(ctx)
        val data = items.toMutableList()
        val adapter = ArrayAdapter(ctx, android.R.layout.simple_list_item_1, data)
        listView.adapter = adapter

        val watcher: TextWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val q = s?.toString()?.trim()?.lowercase().orEmpty()
                val filtered = if (q.isEmpty()) items else items.filter { it.lowercase().contains(q) }
                adapter.clear(); adapter.addAll(filtered); adapter.notifyDataSetChanged()
                emptyView.isVisible = filtered.isEmpty()
            }
            override fun afterTextChanged(s: Editable?) {}
        }
        search.addTextChangedListener(watcher)

        container.addView(search, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        container.addView(emptyView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        container.addView(listView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(400)))

        val dialog = AlertDialog.Builder(ctx).setTitle(title).setView(container).setNegativeButton("Cancel") { d, _ -> d.dismiss(); onChoose(null) }.create()

        listView.setOnItemClickListener { _, _, pos, _ -> val chosen = adapter.getItem(pos); dialog.dismiss(); onChoose(chosen) }
        dialog.show()
    }

    // ---------- DECOR ----------
    private class EqualGapDecoration(private val px: Int) : RecyclerView.ItemDecoration() {
        override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) { outRect.set(px, px, px, px) }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onResume() {
        super.onResume()
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            uiScope.launch {
                try {
                    customCalRepo.syncFromFirebase(uid)
                    fetchMonthEvents()
                } catch (e: Exception) {
                    Log.w(TAG, "Background sync failed", e)
                }
            }
        }
    }
}
