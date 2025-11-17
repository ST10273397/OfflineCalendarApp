package com.example.prog7314progpoe.ui.dashboard

// Android UI
import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.text.TextWatcher
import android.view.*
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.prog7314progpoe.R

// API / repo / models
import com.example.prog7314progpoe.api.ApiClient
import com.example.prog7314progpoe.api.ApiConfig
import com.example.prog7314progpoe.api.Country
import com.example.prog7314progpoe.api.CountryResponse
import com.example.prog7314progpoe.api.HolidayRepository

// Time + coroutines
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

// Firebase helpers
import com.example.prog7314progpoe.database.holidays.FirebaseHolidayDbHelper
import com.example.prog7314progpoe.database.calendar.FirebaseCalendarDbHelper
import com.google.firebase.auth.FirebaseAuth

// UI model
import com.example.prog7314progpoe.ui.dashboard.SlotUiModel

class DashboardFragment : Fragment() {

    // Clock + slots
    private val userZone: ZoneId = ZoneId.systemDefault()

    private lateinit var timeText: TextView
    private lateinit var recycler: RecyclerView
    private lateinit var swipe: SwipeRefreshLayout

    private lateinit var adapter: DashboardSlotsAdapter

    private val timeHandler = Handler(Looper.getMainLooper())
    private val timeUpdater = object : Runnable {
        override fun run() {
            updateTimeNow()
            timeHandler.postDelayed(this, 60_000L)
        }
    }

    private val uiScope = CoroutineScope(Dispatchers.Main + Job())

    private var countries: List<Country> = emptyList()

    // Repo (cache-first)
    private val repo by lazy { HolidayRepository(requireContext()) }

    // Slot assignment model
    private enum class CalType { PUBLIC, CUSTOM }

    private data class SlotAssignment(
        val type: CalType,
        val id: String,           // PUBLIC: iso code  |  CUSTOM: calendarId
        val displayName: String
    )

    /**
     * Per-user SharedPreferences
     * We namespace the file by Firebase UID so each account has independent dashboard slots.
     */
    private fun userPrefs(): SharedPreferences {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: "guest"
        val fileName = "dashboard_slots_$uid"
        return requireContext().getSharedPreferences(fileName, Context.MODE_PRIVATE)
    }

    private fun saveSlot(index: Int, slot: SlotAssignment?) {
        val e = userPrefs().edit()
        if (slot == null) {
            e.remove("type_$index")
            e.remove("id_$index")
            e.remove("name_$index")
        } else {
            e.putString("type_$index", slot.type.name)
            e.putString("id_$index", slot.id)
            e.putString("name_$index", slot.displayName)
        }
        e.apply()
    }

    private fun loadSlot(index: Int): SlotAssignment? {
        val p = userPrefs()
        val type = p.getString("type_$index", null) ?: return null
        val id = p.getString("id_$index", null) ?: return null
        val name = p.getString("name_$index", null) ?: return null
        return SlotAssignment(CalType.valueOf(type), id, name)
    }

    // Lifecycle
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val v = inflater.inflate(R.layout.fragment_dashboard, container, false)
        timeText = v.findViewById(R.id.txtTime)
        recycler = v.findViewById(R.id.recyclerSlots)
        swipe = v.findViewById(R.id.swipe)
        return v
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Grid
        adapter = DashboardSlotsAdapter { index -> onSlotClicked(index) }
        recycler.layoutManager = GridLayoutManager(requireContext(), 2)
        recycler.adapter = adapter
        recycler.addItemDecoration(GridSpacingDecoration(dpToPx(8)))
        recycler.setHasFixedSize(true)

        // Clock
        updateTimeNow()
        timeHandler.removeCallbacks(timeUpdater)
        timeHandler.post(timeUpdater)

        // Data
        renderFromPrefs()
        swipe.setOnRefreshListener { refreshAllSlots() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        timeHandler.removeCallbacks(timeUpdater)
    }

    // Clock UI
    private fun updateTimeNow() {
        val now: ZonedDateTime = ZonedDateTime.now(userZone)
        val text = now.format(DateTimeFormatter.ofPattern("HH:mm — EEE, dd MMM"))
        timeText.text = text
    }

    // Initial render
    private fun renderFromPrefs() {
        val list = MutableList<SlotUiModel>(8) { i ->
            val slot = loadSlot(i)
            if (slot == null) {
                SlotUiModel.Unassigned(i)
            } else {
                SlotUiModel.Populated(i, slot.displayName, "…")
            }
        }
        adapter.submitList(list)
        refreshAllSlots()
    }

    // Refresh all
    private fun refreshAllSlots() {
        swipe.isRefreshing = true
        uiScope.launch {
            val current = adapter.currentList.toMutableList()
            val today = LocalDate.now(userZone)
            for (i in 0 until current.size) {
                val assign = loadSlot(i) ?: continue
                val nextText = when (assign.type) {
                    CalType.PUBLIC -> fetchNextPublicHoliday(assign.id, today)
                    CalType.CUSTOM -> fetchNextCustomEvent(assign.id, today)
                }
                current[i] = SlotUiModel.Populated(i, assign.displayName, nextText)
            }
            adapter.submitList(current)
            swipe.isRefreshing = false
        }
    }

    // Refresh one
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

    // Slot click
    private fun onSlotClicked(index: Int) {
        val existing = loadSlot(index)
        val options = if (existing == null)
            arrayOf(getString(R.string.picker_public_holidays), getString(R.string.picker_custom_calendar))
        else
            arrayOf(getString(R.string.picker_public_holidays), getString(R.string.picker_custom_calendar), "Clear")

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
            }
            .show()
    }

    // Public holidays picker
    private fun pickCountryAndAssign(index: Int) {
        if (countries.isEmpty()) {
            ApiClient.api.getLocations(ApiConfig.apiKey())
                .enqueue(object : Callback<CountryResponse> {
                    override fun onResponse(call: Call<CountryResponse>, response: Response<CountryResponse>) {
                        if (response.isSuccessful) {
                            countries = response.body()?.response?.countries ?: emptyList()
                            showCountryDialog(index)
                        } else {
                            Toast.makeText(requireContext(), "Failed to load countries", Toast.LENGTH_SHORT).show()
                        }
                    }
                    override fun onFailure(call: Call<CountryResponse>, t: Throwable) {
                        Toast.makeText(requireContext(), "Network error loading countries", Toast.LENGTH_SHORT).show()
                    }
                })
        } else {
            showCountryDialog(index)
        }
    }

    private fun showCountryDialog(index: Int) {
        if (countries.isEmpty()) {
            Toast.makeText(requireContext(), "No countries available", Toast.LENGTH_SHORT).show()
            return
        }
        val names = countries.map { it.country_name }
        showSearchableListDialog(
            title = "Pick country",
            items = names,
            emptyHint = "No countries available"
        ) { chosenName ->
            if (chosenName == null) return@showSearchableListDialog
            val pos = names.indexOf(chosenName)
            if (pos < 0) return@showSearchableListDialog
            val country = countries[pos]
            val assign = SlotAssignment(
                type = CalType.PUBLIC,
                id = country.isoCode,
                displayName = country.country_name
            )
            saveSlot(index, assign)
            val list = adapter.currentList.toMutableList()
            list[index] = SlotUiModel.Populated(index, assign.displayName, "…")
            adapter.submitList(list)
            refreshSingleSlot(index)
        }
    }

    // Next public holiday text
    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun fetchNextPublicHoliday(countryIso: String, today: LocalDate): String {
        val fmtOut = DateTimeFormatter.ofPattern("EEE, dd MMM")
        val years = listOf(today.year, today.year + 1)
        return withContext(Dispatchers.IO) {
            val all = mutableListOf<Pair<String, LocalDate>>()
            for (y in years) {
                try {
                    val resp = repo.getHolidays(countryIso, y)
                    val holidays = resp.response?.holidays ?: emptyList()
                    for (h in holidays) {
                        val iso = h.date?.iso ?: continue
                        val title = h.name ?: continue
                        val d = runCatching { LocalDate.parse(iso.substring(0, 10)) }.getOrNull() ?: continue
                        all += title to d
                    }
                } catch (_: Exception) { }
            }
            val next = all.filter { it.second >= today }.minByOrNull { it.second }
            if (next == null) getString(R.string.no_upcoming) else "${next.first} — ${next.second.format(fmtOut)}"
        }
    }

    // Next custom event text (by calendarId)
    private suspend fun fetchNextCustomEvent(calendarId: String, today: LocalDate): String {
        return withContext(Dispatchers.IO) {
            var result = getString(R.string.no_upcoming)
            val latch = CompletableDeferred<Unit>()
            FirebaseHolidayDbHelper.getAllHolidays(calendarId) { holidays ->
                try {
                    val mapped = holidays.mapNotNull { h ->
                        val title = h.name ?: h.javaClass.declaredFields
                            .firstOrNull { it.name == "title" }?.let {
                                it.isAccessible = true; (it.get(h) as? String)
                            } ?: ""
                        val dateStr = h.date?.iso ?: h.date?.toString() ?: h.javaClass.declaredFields
                            .firstOrNull { it.name == "day" }?.let {
                                it.isAccessible = true; (it.get(h) as? String)
                            } ?: ""
                        if (title.isBlank() || dateStr.isBlank()) null else {
                            val d = runCatching { LocalDate.parse(dateStr.substring(0, 10)) }.getOrNull() ?: return@mapNotNull null
                            title to d
                        }
                    }
                    val next = mapped.filter { it.second >= today }.minByOrNull { it.second }
                    result = if (next == null) getString(R.string.no_upcoming)
                    else "${next.first} — ${next.second.format(DateTimeFormatter.ofPattern("EEE, dd MMM"))}"
                } catch (_: Exception) {
                    result = getString(R.string.no_upcoming)
                } finally {
                    latch.complete(Unit)
                }
            }
            latch.await()
            result
        }
    }

    // ---------- CUSTOM CALENDARS PICKER (Firebase) ----------
    private data class CalRow(val id: String, val title: String)

    private fun promptCustomListAndAssign(index: Int) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            Toast.makeText(requireContext(), "Not signed in", Toast.LENGTH_SHORT).show()
            return
        }

        FirebaseCalendarDbHelper.getUserCalendars(uid) { list ->
            val rows = list
                .filter { !it.calendarId.isNullOrBlank() }
                .map { CalRow(it.calendarId!!, it.title ?: "(Untitled)") }
                .sortedBy { it.title.lowercase() }

            if (rows.isEmpty()) {
                Toast.makeText(requireContext(), "No custom calendars yet", Toast.LENGTH_SHORT).show()
                return@getUserCalendars
            }

            val names = rows.map { it.title }
            showSearchableListDialog(
                title = "Choose custom calendar",
                items = names,
                emptyHint = "No custom calendars yet"
            ) { chosenName ->
                if (chosenName == null) return@showSearchableListDialog
                val pos = names.indexOf(chosenName)
                if (pos < 0) return@showSearchableListDialog
                val row = rows[pos]

                val assign = SlotAssignment(
                    type = CalType.CUSTOM,
                    id = row.id,            // real calendarId
                    displayName = row.title
                )
                saveSlot(index, assign)

                val listNow = adapter.currentList.toMutableList()
                listNow[index] = SlotUiModel.Populated(index, assign.displayName, "…")
                adapter.submitList(listNow)
                refreshSingleSlot(index)
            }
        }
    }
    // --------------------------------------------------------

    // Searchable dialog (shared)
    private fun showSearchableListDialog(
        title: String,
        items: List<String>,
        emptyHint: String,
        onChoose: (String?) -> Unit
    ) {
        val ctx = requireContext()

        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(16), dpToPx(8), dpToPx(16), 0)
        }

        val search = EditText(ctx).apply {
            hint = "Search…"
            inputType = InputType.TYPE_CLASS_TEXT
        }

        val emptyView = TextView(ctx).apply {
            text = emptyHint
            setPadding(0, dpToPx(24), 0, dpToPx(24))
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
                emptyView.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        }
        search.addTextChangedListener(watcher)

        container.addView(search, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        container.addView(emptyView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        container.addView(listView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(400)))

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

    // Helpers
    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density + 0.5f).toInt()
    }

    private class GridSpacingDecoration(private val space: Int) : RecyclerView.ItemDecoration() {
        override fun getItemOffsets(
            outRect: android.graphics.Rect,
            view: View,
            parent: RecyclerView,
            state: RecyclerView.State
        ) {
            outRect.set(space, space, space, space)
        }
    }
}
