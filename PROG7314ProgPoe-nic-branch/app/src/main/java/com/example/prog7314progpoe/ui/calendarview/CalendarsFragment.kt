package com.example.prog7314progpoe.ui.calendarview

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
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
import com.example.prog7314progpoe.database.calendar.FirebaseCalendarDbHelper
import com.example.prog7314progpoe.database.holidays.FirebaseHolidayDbHelper
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

//CALENDAR FRAGMENT - monthly grid with pickers and a day card
//-----------------------------------------------------------------------------------------------
class CalendarsFragment : Fragment() {

    //STATE - month and chosen sources
    //-----------------------------------------------------------------------------------------------
    private var currentMonth: YearMonth = YearMonth.now() // which month we show
    private var selectedDay: LocalDate = LocalDate.now() // selected day for details

    private val activeCountryIsos = mutableSetOf<String>() // public holiday country isos
    private val activeCustomIds = mutableSetOf<String>() // custom calendar ids
    private val customIdToName = mutableMapOf<String, String>() // id -> title (for tags)

    private var countries: List<Country> = emptyList() // cached countries
    private val monthEvents = mutableMapOf<LocalDate, MutableList<EventItem>>() // events per date
    //-----------------------------------------------------------------------------------------------

    //UI - top header grid and day card
    //-----------------------------------------------------------------------------------------------
    private lateinit var txtMonth: TextView // month label
    private lateinit var btnPrev: ImageButton // go left
    private lateinit var btnNext: ImageButton // go right
    private lateinit var btnPickSingle: Button // pick exactly one source
    private lateinit var btnPickFromDashboard: Button // pick many from dashboard
    private lateinit var grid: RecyclerView // month grid
    private lateinit var dayCard: LinearLayout // details card
    private lateinit var dayCardTitle: TextView // title in card
    private lateinit var dayCardList: LinearLayout // list of events in card
    private lateinit var emptyDayText: TextView // empty state text
    //-----------------------------------------------------------------------------------------------

    //COROUTINES - scope for async work
    //-----------------------------------------------------------------------------------------------
    private val uiScope = CoroutineScope(Dispatchers.Main + SupervisorJob()) // ui scope
    //-----------------------------------------------------------------------------------------------

    // PER-USER PREFS (matches DashboardFragment)
    //-----------------------------------------------------------------------------------------------
    private fun userPrefs() = requireContext()
        .getSharedPreferences("dashboard_slots_${FirebaseAuth.getInstance().currentUser?.uid ?: "guest"}", Context.MODE_PRIVATE)
    //-----------------------------------------------------------------------------------------------

    //REPO - cache first access
    //-----------------------------------------------------------------------------------------------
    private val repo by lazy { HolidayRepository(requireContext()) } // central repo
    //-----------------------------------------------------------------------------------------------

    //MODELS - tiny event holder and kind
    //-----------------------------------------------------------------------------------------------
    private data class EventItem(
        val date: LocalDate, // day only
        val title: String, // event title
        val sourceLabel: String, // ex South Africa or custom calendar title
        val sourceKind: SourceKind // public or custom
    )

    private enum class SourceKind { PUBLIC, CUSTOM } // two kinds
    //-----------------------------------------------------------------------------------------------

    //LIFECYCLE - inflate and wire
    //-----------------------------------------------------------------------------------------------
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_calendars_month, container, false) // inflate layout
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        //BIND VIEWS - grab handles
        //-----------------------------------------------------------------------------------------------
        txtMonth = view.findViewById(R.id.txtMonth) // month text
        btnPrev = view.findViewById(R.id.btnPrev) // prev button
        btnNext = view.findViewById(R.id.btnNext) // next button
        btnPickSingle = view.findViewById(R.id.btnPickSingle) // single picker
        btnPickFromDashboard = view.findViewById(R.id.btnPickFromDashboard) // multi picker
        grid = view.findViewById(R.id.calendarGrid) // month grid
        dayCard = view.findViewById(R.id.dayCard) // details card
        dayCardTitle = view.findViewById(R.id.dayCardTitle) // card title
        dayCardList = view.findViewById(R.id.dayCardList) // card list
        emptyDayText = view.findViewById(R.id.emptyDayText) // empty text
        //-----------------------------------------------------------------------------------------------

        //BOTTOM INSET PADDING - keep the day card above the bottom nav
        //-----------------------------------------------------------------------------------------------
        val root = view as ViewGroup // root container
        val bottomNav = requireActivity().findViewById<BottomNavigationView>(R.id.bottom_nav)

        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val sysBottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            val navH = bottomNav?.height ?: 0
            val extra = dp(16)
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, sysBottom + navH + extra)
            insets
        }
        //-----------------------------------------------------------------------------------------------

        //GRID SETUP - 7 columns with equal gaps
        //-----------------------------------------------------------------------------------------------
        grid.layoutManager = GridLayoutManager(requireContext(), 7)
        grid.adapter = DaysAdapter { clicked -> onDayClicked(clicked) }
        grid.addItemDecoration(EqualGapDecoration(dp(4)))
        grid.setHasFixedSize(true)
        //-----------------------------------------------------------------------------------------------

        //MONTH NAV - move back or forward
        //-----------------------------------------------------------------------------------------------
        btnPrev.setOnClickListener {
            currentMonth = currentMonth.minusMonths(1)
            refreshMonth()
        }
        btnNext.setOnClickListener {
            currentMonth = currentMonth.plusMonths(1)
            refreshMonth()
        }
        //-----------------------------------------------------------------------------------------------

        //PICKERS - single vs from dashboard
        //-----------------------------------------------------------------------------------------------
        btnPickSingle.setOnClickListener { onPickSingleCalendar() }
        btnPickFromDashboard.setOnClickListener { onPickFromDashboard() }
        //-----------------------------------------------------------------------------------------------

        //INITIAL STATE - reset selections and draw
        //-----------------------------------------------------------------------------------------------
        selectedDay = LocalDate.now()
        activeCountryIsos.clear()
        activeCustomIds.clear()
        customIdToName.clear()
        refreshMonth()
        //-----------------------------------------------------------------------------------------------
    }

    override fun onDestroyView() {
        super.onDestroyView()
        uiScope.coroutineContext.cancelChildren() // stop pending work
    }
    //-----------------------------------------------------------------------------------------------

    //DAY CLICK - select and show details
    //-----------------------------------------------------------------------------------------------
    private fun onDayClicked(day: LocalDate) {
        selectedDay = day
        renderDayCard()
        grid.adapter?.notifyDataSetChanged()
    }
    //-----------------------------------------------------------------------------------------------

    //REFRESH MONTH - rebuild grid load events and render card
    //-----------------------------------------------------------------------------------------------
    private fun refreshMonth() {
        txtMonth.text = currentMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
        renderGrid()
        fetchMonthEvents()
        renderDayCard()
    }
    //-----------------------------------------------------------------------------------------------

    //GRID RENDER - compute full weeks with lead and trail days
    //-----------------------------------------------------------------------------------------------
    private fun renderGrid() {
        val firstOfMonth = currentMonth.atDay(1)
        val firstDow = firstOfMonth.dayOfWeek.value % 7 // sunday index as 0
        val daysInMonth = currentMonth.lengthOfMonth()

        val cells = mutableListOf<LocalDate>()
        for (i in 0 until firstDow) {
            cells.add(firstOfMonth.minusDays((firstDow - i).toLong()))
        }
        for (d in 1..daysInMonth) {
            cells.add(currentMonth.atDay(d))
        }
        while (cells.size % 7 != 0) {
            val last = cells.last()
            cells.add(last.plusDays(1))
        }
        (grid.adapter as DaysAdapter).submit(cells)
    }
    //-----------------------------------------------------------------------------------------------

    //FETCH EVENTS - fill monthEvents map from active sources
    //-----------------------------------------------------------------------------------------------
    private fun fetchMonthEvents() {
        monthEvents.clear()

        val start = currentMonth.atDay(1)
        val end = currentMonth.atEndOfMonth()
        val yearList = listOf(start.year, start.year + 1)

        uiScope.launch {
            // PUBLIC HOLIDAYS
            for (iso in activeCountryIsos) {
                for (y in yearList) {
                    try {
                        val resp = withContext(Dispatchers.IO) { repo.getHolidays(iso, y) }
                        val hols = resp.response?.holidays ?: emptyList()
                        hols.forEach { h ->
                            val isoDate = h.date?.iso ?: return@forEach
                            val d = runCatching { LocalDate.parse(isoDate.substring(0, 10)) }.getOrNull() ?: return@forEach
                            if (!d.isBefore(start) && !d.isAfter(end)) {
                                val title = h.name ?: "Holiday"
                                val label = resolveCountryName(iso)
                                monthEvents.getOrPut(d) { mutableListOf() }
                                    .add(EventItem(d, title, label, SourceKind.PUBLIC))
                            }
                        }
                    } catch (_: Exception) { /* ignore */ }
                }
            }

            // CUSTOM CALENDARS
            for (cid in activeCustomIds) {
                val latch = CompletableDeferred<Unit>()
                FirebaseHolidayDbHelper.getAllHolidays(cid) { list ->
                    try {
                        val label = customIdToName[cid] ?: cid // show nice title if we have it
                        list.forEach { h ->
                            val title = tryField(h, "title") ?: tryField(h, "name") ?: "Event"
                            val dateStr = tryDateIso(h) ?: tryField(h, "day")
                            val d = runCatching { LocalDate.parse(dateStr ?: "") }.getOrNull()
                            if (d != null && !d.isBefore(start) && !d.isAfter(end)) {
                                monthEvents.getOrPut(d) { mutableListOf() }
                                    .add(EventItem(d, title, label, SourceKind.CUSTOM))
                            }
                        }
                    } catch (_: Exception) { /* ignore */ }
                    finally { latch.complete(Unit) }
                }
                latch.await()
            }

            grid.adapter?.notifyDataSetChanged()
            renderDayCard()
        }
    }
    //-----------------------------------------------------------------------------------------------

    //DAY CARD - show all events for the selected day
    //-----------------------------------------------------------------------------------------------
    private fun renderDayCard() {
        val items = monthEvents[selectedDay].orEmpty()
        dayCardTitle.text = selectedDay.format(DateTimeFormatter.ofPattern("EEE, dd MMM"))
        dayCard.visibility = View.VISIBLE

        dayCardList.removeAllViews()
        if (items.isEmpty()) {
            emptyDayText.isVisible = true
        } else {
            emptyDayText.isVisible = false
            items.sortedBy { it.title }.forEach { ev ->
                dayCardList.addView(makeEventRow(ev))
            }
        }
    }
    //-----------------------------------------------------------------------------------------------

    //PICK SINGLE - choose one calendar from full list (public + user custom)
    //-----------------------------------------------------------------------------------------------
    private fun onPickSingleCalendar() {
        // Step 1: ensure country list
        val ensureCountries = CompletableDeferred<Unit>()
        if (countries.isEmpty()) {
            ApiClient.api.getLocations(ApiConfig.apiKey())
                .enqueue(object : Callback<CountryResponse> {
                    override fun onResponse(call: Call<CountryResponse>, response: Response<CountryResponse>) {
                        countries = response.body()?.response?.countries ?: emptyList()
                        ensureCountries.complete(Unit)
                    }
                    override fun onFailure(call: Call<CountryResponse>, t: Throwable) {
                        countries = emptyList()
                        ensureCountries.complete(Unit)
                    }
                })
        } else {
            ensureCountries.complete(Unit)
        }

        // Step 2: load user’s custom calendars from Firebase
        val ensureCustoms = CompletableDeferred<List<Pair<String, String>>>() // list of (id,title)
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            ensureCustoms.complete(emptyList())
        } else {
            FirebaseCalendarDbHelper.getUserCalendars(uid) { list ->
                val rows = list
                    .filter { !it.calendarId.isNullOrBlank() }
                    .map { it.calendarId!! to (it.title ?: "(Untitled)") }
                    .sortedBy { it.second.lowercase() }
                ensureCustoms.complete(rows)
            }
        }

        uiScope.launch {
            ensureCountries.await()
            val customRows = ensureCustoms.await() // (id, title)

            // Merge lists
            val display = mutableListOf<String>()
            val types = mutableListOf<SourceKind>()
            val ids = mutableListOf<String>()
            val labels = mutableListOf<String>() // for custom names or country names

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

            showSearchableListDialog(
                title = "Pick calendar",
                items = display,
                emptyHint = "No calendars found"
            ) { chosen ->
                if (chosen == null) return@showSearchableListDialog
                val idx = display.indexOf(chosen)
                if (idx < 0) return@showSearchableListDialog

                // Reset & set the selection
                activeCountryIsos.clear()
                activeCustomIds.clear()
                customIdToName.clear()

                when (types[idx]) {
                    SourceKind.PUBLIC -> {
                        activeCountryIsos += ids[idx]
                        // (no need to store label for public; we resolve from iso later)
                    }
                    SourceKind.CUSTOM -> {
                        val id = ids[idx]
                        val name = labels[idx]
                        activeCustomIds += id
                        customIdToName[id] = name // keep title for tags
                    }
                }
                refreshMonth()
            }
        }
    }
    //-----------------------------------------------------------------------------------------------

    //PICK FROM DASHBOARD - choose multiple saved slots
    //-----------------------------------------------------------------------------------------------
    private fun onPickFromDashboard() {
        val slots = readDashboardSlots()
        if (slots.isEmpty()) {
            Toast.makeText(requireContext(), "No dashboard calendars to choose", Toast.LENGTH_SHORT).show()
            return
        }
        val names = slots.map { it.displayName }
        val checked = BooleanArray(slots.size) { i ->
            val s = slots[i]
            (s.kind == SourceKind.PUBLIC && activeCountryIsos.contains(s.id)) ||
                    (s.kind == SourceKind.CUSTOM && activeCustomIds.contains(s.id))
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Pick from dashboard")
            .setMultiChoiceItems(names.toTypedArray(), checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton("Apply") { d, _ ->
                activeCountryIsos.clear()
                activeCustomIds.clear()
                // also rebuild the id->name map for customs so tags show properly
                customIdToName.clear()

                checked.forEachIndexed { i, flag ->
                    if (flag) {
                        val s = slots[i]
                        if (s.kind == SourceKind.PUBLIC) {
                            activeCountryIsos += s.id
                        } else {
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
    //-----------------------------------------------------------------------------------------------

    //HELPERS - read dashboard slots stored in per-user prefs
    //-----------------------------------------------------------------------------------------------
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
    //-----------------------------------------------------------------------------------------------

    //UI FACTORY - build one row for the day card
    //-----------------------------------------------------------------------------------------------
    private fun makeEventRow(ev: EventItem): View {
        val ctx = requireContext()
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val tag = TextView(ctx).apply {
            // Always use first-two-letter tag (public: country name, custom: calendar title)
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
    //-----------------------------------------------------------------------------------------------

    //ADAPTER - month grid with 7 columns
    //-----------------------------------------------------------------------------------------------
    private inner class DaysAdapter(val onClick: (LocalDate) -> Unit) :
        RecyclerView.Adapter<DayVH>() {

        private val days = mutableListOf<LocalDate>()

        fun submit(list: List<LocalDate>) {
            days.clear()
            days.addAll(list)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayVH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_day_cell, parent, false)
            return DayVH(v)
        }

        override fun onBindViewHolder(holder: DayVH, position: Int) {
            holder.bind(days[position])
        }

        override fun getItemCount(): Int = days.size
    }

    private inner class DayVH(v: View) : RecyclerView.ViewHolder(v) {
        private val txtDay: TextView = v.findViewById(R.id.txtDay)
        private val txtTag: TextView = v.findViewById(R.id.txtTag)

        fun bind(day: LocalDate) {
            txtDay.text = day.dayOfMonth.toString()

            val isToday = day == LocalDate.now()
            val isSel = day == selectedDay
            itemView.isSelected = isSel

            if (isToday) {
                txtDay.setTextColor(Color.parseColor("#1565C0"))
            } else {
                txtDay.setTextColor(Color.parseColor("#000000"))
            }

            val inMonth = day.month == currentMonth.month
            txtDay.alpha = if (inMonth) 1f else 0.4f

            val evs = monthEvents[day].orEmpty()
            if (evs.isNotEmpty()) {
                // base tag uses first two letters of the *source label* (country name or calendar title)
                val base = shortTag(evs.first().sourceLabel)
                val tagTextStr = if (evs.size > 1) "$base+" else base
                txtTag.text = tagTextStr
                txtTag.setTextColor(Color.parseColor("#1565C0"))
                txtTag.visibility = View.VISIBLE
            } else {
                txtTag.visibility = View.GONE
            }

            itemView.setOnClickListener { onDayClicked(day) }
        }
    }
    //-----------------------------------------------------------------------------------------------

    //UTILS - tag text and reflection helpers
    //-----------------------------------------------------------------------------------------------
    private fun shortTag(name: String): String {
        val s = name.trim()
        // Use first two *letters* if possible; fallback to first two chars
        val letters = s.replace(Regex("[^\\p{L}]"), "")
        val base = if (letters.length >= 2) letters.substring(0, 2) else s.take(2)
        return base.uppercase()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density + 0.5f).toInt()

    private fun resolveCountryName(iso: String): String {
        val c = countries.firstOrNull { it.isoCode == iso }
        return c?.country_name ?: iso
    }

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
    //-----------------------------------------------------------------------------------------------

    //SEARCHABLE DIALOG - generic list with search box
    //-----------------------------------------------------------------------------------------------
    private fun showSearchableListDialog(
        title: String,
        items: List<String>,
        emptyHint: String,
        onChoose: (String?) -> Unit
    ) {
        val ctx = requireContext()

        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), 0)
        }

        val search = EditText(ctx).apply {
            hint = "Search…"
            inputType = InputType.TYPE_CLASS_TEXT
        }

        val emptyView = TextView(ctx).apply {
            text = emptyHint
            setPadding(0, dp(24), 0, dp(24))
            gravity = Gravity.CENTER
            visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        }

        val listView = ListView(ctx)
        val data = items.toMutableList()
        val adapter = ArrayAdapter(ctx, android.R.layout.simple_list_item_1, data)
        listView.adapter = adapter

        val watcher: TextWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val q = s?.toString()?.trim()?.lowercase().orEmpty()
                val filtered = if (q.isEmpty()) items else items.filter { it.lowercase().contains(q) }
                adapter.clear()
                adapter.addAll(filtered)
                adapter.notifyDataSetChanged()
                emptyView.isVisible = filtered.isEmpty()
            }
            override fun afterTextChanged(s: Editable?) {}
        }
        search.addTextChangedListener(watcher)

        container.addView(search, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        container.addView(emptyView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        container.addView(listView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(400)))

        val dialog = AlertDialog.Builder(ctx)
            .setTitle(title)
            .setView(container)
            .setNegativeButton("Cancel") { d, _ -> d.dismiss(); onChoose(null) }
            .create()

        listView.setOnItemClickListener { _, _, pos, _ ->
            val chosen = adapter.getItem(pos)
            dialog.dismiss()
            onChoose(chosen)
        }

        dialog.show()
    }
    //-----------------------------------------------------------------------------------------------

    //EQUAL GAP - simple spacing decorator for grid
    //-----------------------------------------------------------------------------------------------
    private class EqualGapDecoration(private val px: Int) : RecyclerView.ItemDecoration() {
        override fun getItemOffsets(
            outRect: Rect,
            view: View,
            parent: RecyclerView,
            state: RecyclerView.State
        ) {
            outRect.set(px, px, px, px)
        }
    }
    //-----------------------------------------------------------------------------------------------

}