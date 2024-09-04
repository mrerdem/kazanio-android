package com.creadeep.kazanio

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.preference.PreferenceManager
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.LinearLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import androidx.viewpager.widget.ViewPager
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.google.android.material.tabs.TabLayoutMediator.TabConfigurationStrategy
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

class TicketsFragment : Fragment() {

    private lateinit var tabLayout: TabLayout // Horizontal tab layout for different game types
    private lateinit var viewPager: ViewPager // Horizontal view pager for different game types
    private lateinit var adapter: GameTypePagerAdapter // For different game types
    private lateinit var tabLayoutMediator: TabLayoutMediator
    private lateinit var loc: Locale
    private lateinit var simpleDateFormatWithoutSep: SimpleDateFormat // Without separators (ddMMyyyy)
    private lateinit var simpleDateFormatWithSep: SimpleDateFormat // With separators (dd/MM/yyyy)
    private lateinit var scrollView: NestedScrollView // For bottom sheet
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>
    private lateinit var gameTypeAdapter: NoFilterArrayAdapter<String>
    private lateinit var rowNumberAdapter: NoFilterDisabledItemsArrayAdapter<String>
    private lateinit var coordinatorLayout: CoordinatorLayout
    private lateinit var focusStealer: View // Used to prevent scrollview scroll to bottom by focusing this item located at the top
    private lateinit var selectedNumbers: Array<String> // Stores user selected numbers
    private lateinit var generatorTabAdapter: GeneratorViewPagerAdapter // Adapter for rows containing number selectors
    private lateinit var viewPagerRows: ViewPager2 // To create swipe layout for number selector for each row
    private var currentSelectionRow: Int = 0// Index of currently active number selection row
    private var gameIndex: Int = 0 // Index of currently selected game type
    private var rowNum: Int = 1 // Number of currently selected rows

    private lateinit var gameTypeName: AutoCompleteTextView // Game type selector
    private lateinit var rowNumberValue: AutoCompleteTextView // Row number selector
    private lateinit var rowNumberLayout: TextInputLayout // Layout for row number
    private lateinit var fractionValue: AutoCompleteTextView // Fraction selector
    private lateinit var fractionLayout: TextInputLayout // Layout for fraction
    private lateinit var methodSelector: MaterialButtonToggleGroup // Segmented button group for manual/auto number generation
    private lateinit var numberLayout: ConstraintLayout // Number selector with tab layout
    private lateinit var numberLayoutMp: LinearLayout // Number selector with text edit for Milli Piyango
    private lateinit var dateValue: TextInputEditText
    private lateinit var numberEntryMp: TextInputEditText
    private lateinit var gameNames: Array<String>
    private lateinit var tabLayoutRows: TabLayout
    private lateinit var buttonCancel: Button
    private lateinit var buttonAdd: Button

    private lateinit var sharedPref: SharedPreferences

    private lateinit var fragmentView: View
    private var fragmentUiStatusCallback // Used to inform activity when fragment's views are available to be referenced in the tutorial
            : FragmentUiStatusListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        retainInstance = true
        setHasOptionsMenu(true) // To use top menu, make the parameter true and load menu xml in oncreateoptionsmenu()
        // Get locale
        loc = when (Locale.getDefault().language) {
            "tr" ->
                Locale("tr", "TR")
            else ->
                Locale("en", "US")
        }
        // Set date formats
        simpleDateFormatWithoutSep = SimpleDateFormat("ddMMyyyy", loc)
        simpleDateFormatWithSep = SimpleDateFormat("dd/MM/yyyy", loc)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_tickets, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set view references
        fragmentView = view
        tabLayout = view.findViewById(R.id.tabLayout) // For different lottery types
        viewPager = view.findViewById(R.id.view_pager) // For different lottery types
        coordinatorLayout = view.findViewById(R.id.coordinatorLayout) // For snackbar
        scrollView = view.findViewById(R.id.scroll_view) // For ticket generator bottom sheet

        // Hide bottom sheet
        bottomSheetBehavior = BottomSheetBehavior.from(scrollView)
        bottomSheetBehavior.isHideable = true
        bottomSheetBehavior.peekHeight = 0

        // Get resources
        sharedPref = PreferenceManager.getDefaultSharedPreferences(context)
        gameNames = resources.getStringArray(R.array.game_names)
        gameIndex = sharedPref.getString("defaultGameType", "1")!!.toInt()

        // Populate pages for different lottery types with the adapter using tab titles
        if (viewPager.adapter == null) {
            // Prepare lottery tab titles
            val tabNames: MutableList<String> = ArrayList()
            tabNames.add(resources.getString(R.string.tab_name_all)) // Add only the first tab's title
            tabNames.addAll(listOf(*resources.getStringArray(R.array.game_names))) // Add game names as remaining titles
            // Set adapter
            adapter = GameTypePagerAdapter(childFragmentManager, 1) // Using getChildFragmentManager() is vital. Since it is child fragment, getSupportFragmentManager() causes adapter not found and empty pages
            for (i in tabNames.indices) {
                adapter.addFragment(TicketsSingleTypeFragment(i), tabNames[i])
            }
            viewPager.adapter = adapter
            tabLayout.setupWithViewPager(viewPager)
        }
    }

    override fun onResume() {
        // Inform activity saying fragment is ready so that tab layout can be referenced in the tutorial
        if (!sharedPref.getBoolean("isScanningTutorialShown", false))
            fragmentUiStatusCallback?.onFragmentUiAvailable()
        super.onResume()
    }

    /**
     * Populates bottom sheet. Used to separate preparing bottom sheet from fragment load since it causes delay.
     */
    private fun populateBottomSheet() {
        // Row number listener
        rowNumberValue.addTextChangedListener(object: TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (s != null && s.toString() != "") {
                    rowNum = s.toString().toInt()
                    // Create tabs for number selectors in each row
                    val rowNames = resources.getStringArray(R.array.row_names).take(rowNum)
                    gameIndex = gameNames.indexOf(gameTypeName.text.toString())
                    generatorTabAdapter = GeneratorViewPagerAdapter(gameIndex + 1, rowNames)
                    viewPagerRows.adapter = generatorTabAdapter
                    viewPagerRows.registerOnPageChangeCallback(object: ViewPager2.OnPageChangeCallback() {
                        override fun onPageSelected(position: Int) { // Somehow running twice
                            super.onPageSelected(position)
                            // Update selected numbers
                            storeSelectedNumbers(currentSelectionRow)
                            currentSelectionRow = position
                            // Update chips after OnBindViewHolder finishes
                            updateChips()
                        }
                    })

                    // Inform tab layout when viewpager is used to change current tab via swipe
                    tabLayoutMediator = TabLayoutMediator(tabLayoutRows, viewPagerRows, true, TabConfigurationStrategy { tab, position ->
                        tab.text = rowNames[position]
                    })
                    tabLayoutMediator.attach()

                    // Initialize selected number string
                    selectedNumbers = Array(rowNum){""}

                    // Reset method selector
                    methodSelector.check(R.id.button_method_manual)
                }
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            }
        })

        // Listen to game type changes in generator bottom sheet
        gameTypeName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            }

            override fun afterTextChanged(name: Editable) { // Update UI based on selected game type
                // Set fields
                val gameIndex = gameNames.indexOf(name.toString())
                when (gameIndex) {
                    0 -> { // Milli Piyango
                        rowNumberLayout.visibility = View.GONE // Hide unnecessary fields
                        methodSelector.visibility = View.GONE
                        numberLayout.visibility = View.GONE
                        numberLayoutMp.visibility = View.VISIBLE // Show required fields
                        fractionLayout.visibility = View.VISIBLE

                        // Remove focus from number entry area after keyboard done clicked & close keyboard
                        numberEntryMp.setOnEditorActionListener {_, action, _ ->
                            if (action == EditorInfo.IME_ACTION_DONE) {
                                numberEntryMp.clearFocus()
                                closeKeyboard()
                            }
                            true
                        }

                        // Set fraction
                        val fractionList = arrayOf(resources.getString(R.string.mp_fraction_full),
                                resources.getString(R.string.mp_fraction_half),
                                resources.getString(R.string.mp_fraction_quarter))
                        val adapter = NoFilterArrayAdapter(requireContext(), R.layout.dropdown_menu_popup_item, fractionList)
                        fractionValue.setText(resources.getString(R.string.mp_fraction_full), false)
                        fractionValue.setAdapter(adapter)
                    }
                    5, 6 -> { // Super Piyango, Banko Piyango
                        rowNumberLayout.visibility = View.GONE // Hide unnecessary fields
                        methodSelector.visibility = View.GONE
                        numberLayout.visibility = View.GONE
                        fractionLayout.visibility = View.GONE
                        numberLayoutMp.visibility = View.VISIBLE // Show required field

                        // Remove focus from number entry area after keyboard done clicked & close keyboard
                        numberEntryMp.setOnEditorActionListener {_, action, _ ->
                            if (action == EditorInfo.IME_ACTION_DONE) {
                                numberEntryMp.clearFocus()
                                closeKeyboard()
                            }
                            true
                        }
                    }
                    else -> { // Sayisal Loto, Super Loto, On Numara, Sans Topu, Super Sayisal Loto, Para Loto
                        rowNumberLayout.visibility = View.VISIBLE // Show required fields
                        methodSelector.visibility = View.VISIBLE
                        numberLayout.visibility = View.VISIBLE
                        numberLayoutMp.visibility = View.GONE // Hide unnecessary fields
                        fractionLayout.visibility = View.GONE

                        // Set row number dropdown menu items
                        val rowVariations = resources.getIntArray(R.array.game_row_variations)
                        val rowsList = (1 .. rowVariations[gameIndex]).toList().toTypedArray()
                        var stringList = rowsList.map { it.toString() }.toTypedArray()
                        // Add (Soon) suffix where necessary
                        for (i in stringList.indices) {
                            if (i > 3)
                                stringList[i] = "${stringList[i]} ${context?.resources?.getString(R.string.row_number_action_suffix)}"
                        }
                        rowNumberAdapter = NoFilterDisabledItemsArrayAdapter(
                                requireContext(),
                                R.layout.dropdown_menu_popup_item,
                                stringList)
                        rowNumberValue.setText(sharedPref.getString("defaultRowNumber", "1"), false)
                        rowNumberValue.setAdapter(rowNumberAdapter)
                    }
                }

                // Set default date text to next possible
                val calendar = GameUtils.findNextDrawDateAfter(Calendar.getInstance(), gameIndex + 1)
                val dateString = simpleDateFormatWithSep.format(calendar.time) + " - " + calendar.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, loc)
                dateValue.setText(dateString)
                focusStealer.requestFocus()
            }
        })

        // Populate lottery type dropdown
        if (gameTypeName.adapter == null) {
            gameTypeAdapter = NoFilterArrayAdapter(
                    requireContext(),
                    R.layout.dropdown_menu_popup_item,
                    gameNames)
            gameTypeName.setText(gameNames[Integer.parseInt(sharedPref.getString("defaultGameType", "2")!!) - 1], false) // Set default value
            gameTypeName.setAdapter(gameTypeAdapter) // Set values for dropdown
        }

        // Open date picker when date selector is clicked
        dateValue.setOnClickListener {
            val builder = MaterialDatePicker.Builder.datePicker()
            val gameIndex = gameNames.indexOf(gameTypeName.text.toString())
            builder.setTitleText(resources.getStringArray(R.array.date_picker_definitions)[gameIndex])
            val picker = builder.build()
            picker.addOnPositiveButtonClickListener {
                val cal = Calendar.getInstance()
                cal.timeInMillis = it
                if (TicketUtils.isDateInvalid(simpleDateFormatWithoutSep.format(cal.time), gameIndex + 1))
                    Snackbar.make(fragmentView, resources.getString(R.string.incorrect_date_warning), Snackbar.LENGTH_LONG).show()
                while (TicketUtils.isDateInvalid(simpleDateFormatWithoutSep.format(cal.time), gameIndex + 1)) {
                    cal.add(Calendar.DATE, 1)
                }
                val dateString = simpleDateFormatWithSep.format(cal.time) + " - " + cal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, loc)
                dateValue.setText(dateString)
            }
            picker.show(parentFragmentManager, picker.toString())
        }

        // Set random number selection functionality
        methodSelector.check(R.id.button_method_manual)
        methodSelector.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.button_method_manual -> {
                        // Reset number selections
                        rowNumberValue.text = rowNumberValue.text // Resets chips
                        selectedNumbers = Array(rowNum){""}
                    }
                    R.id.button_method_random, R.id.button_method_most_freq, R.id.button_method_least_freq -> {
                        val numAmount: Int
                        val numbersList: ArrayList<Int>
                        when (gameIndex) {
                            1, 7 -> { // Sayisal Loto, Super Sayisal Loto
                                numAmount = 6
                                numbersList = when (checkedId) {
                                    R.id.button_method_most_freq -> ArrayList(listOf(38, 18, 17, 1, 21, 16, 26, 40, 5, 30, 46, 3, 22, 32, 36, 9, 14, 15, 41, 47, 12, 20, 27, 39, 13))
                                    R.id.button_method_least_freq -> ArrayList(listOf(43, 37, 33, 45, 31, 28, 48, 35, 24, 6, 23, 44, 29, 49, 10, 11, 4, 2, 42, 34, 7, 25, 19, 8))
                                    else -> ArrayList((1 .. 49).toList())
                                }
                            }
                            2, 8 -> { // Super Loto, Para Loto
                                numAmount = 6
                                numbersList = when (checkedId) {
                                    R.id.button_method_most_freq -> ArrayList(listOf(13, 37, 35, 47, 1, 20, 45, 17, 36, 43, 46, 4, 22, 18, 19, 2, 5, 39, 40, 12, 24, 33, 38, 10, 26, 54))
                                    R.id.button_method_least_freq -> ArrayList(listOf(52, 16, 42, 32, 25, 9, 30, 53, 28, 27, 21, 11, 8, 7, 3, 48, 34, 6, 51, 50, 44, 15, 41, 31, 29, 23, 14))
                                    else -> ArrayList((1..54).toList())
                                }
                            }
                            3, 9 -> { // On Numara, Super On Numara
                                numAmount = 10
                                numbersList = when (checkedId) {
                                    R.id.button_method_most_freq -> ArrayList(listOf(26, 35, 72, 3, 42, 77, 5, 12, 71, 7, 23, 62, 4, 46, 45, 50, 69, 73, 30, 68, 2, 18, 33, 19, 59, 17, 37, 51, 1, 44, 21, 24, 48, 61, 22, 32, 36, 57, 10, 15))
                                    R.id.button_method_least_freq -> ArrayList(listOf(13, 9, 20, 40, 55, 67, 58, 29, 6, 54, 80, 70, 25, 79, 47, 31, 8, 65, 63, 52, 43, 11, 76, 74, 28, 14, 49, 16, 64, 41, 66, 53, 75, 60, 45, 39, 27, 78, 56, 38))
                                    else -> ArrayList((1..80).toList())
                                }
                            }
                            else -> { // Sans Topu, Super Sans Topu
                                numAmount = 5
                                numbersList = when (checkedId) {
                                    R.id.button_method_most_freq -> ArrayList(listOf(29, 22, 15, 9, 23, 2, 6, 12, 5, 14, 17, 25, 24, 26, 20, 11, 10))
                                    R.id.button_method_least_freq -> ArrayList(listOf(21, 34, 33, 30, 31, 27, 16, 3, 32, 4, 18, 1, 8, 7, 28, 19, 13))
                                    else -> ArrayList((1..34).toList())
                                }
                            }
                        }
                        selectedNumbers = Array(rowNum) { "" }
                        for (i in 0 until rowNum) {
                            numbersList.shuffle()
                            for (j in 0 until numAmount) {
                                selectedNumbers[i] = "${selectedNumbers[i]}${String.format("%02d", numbersList[j])}"
                            }
                            selectedNumbers[i] = TicketUtils.orderNumbers(selectedNumbers[i]) // Put 2-digit numbers in increasing order
                        }
                        // Pick extra number for Sans Topu
                        if (gameIndex == 4 || gameIndex == 10) {
                            for (i in 0 until rowNum) {
                                val numbersList2 = when (checkedId) {
                                    R.id.button_method_most_freq -> ArrayList(listOf(6, 10, 12, 2, 4, 14, 11))
                                    R.id.button_method_least_freq -> ArrayList(listOf(8, 1, 7, 5, 3, 9, 13))
                                    else -> ArrayList((1..14).toList())
                                }
                                numbersList2.shuffle()
                                selectedNumbers[i] = "${selectedNumbers[i]}${String.format("%02d", numbersList2[0])}"
                            }
                        }
                        // Update chips to show selected numbers
                        updateChips()
                    }
                }
            }
        }

        // Set bottom buttons' functionality
        buttonCancel.setOnClickListener {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            closeKeyboard()
        }
        buttonAdd.setOnClickListener {
            closeKeyboard()
            // Prepare ticket
            val gameIndex = gameNames.indexOf(gameTypeName.text.toString())
            val bundleTicket = Ticket()
            var isNumberOk = true // Represents if the length of the selected number is ok
            bundleTicket.gameType = gameIndex + 1
            bundleTicket.date = dateValue.text.toString().substring(0, 10).replace("/", "")
            when (gameIndex) {
                0 -> { // Milli Piyango
                    bundleTicket.ticketType = 1 // Terminal MP
                    // Fraction
                    when (fractionValue.text.toString()) {
                        resources.getString(R.string.mp_fraction_full) -> bundleTicket.fraction = "11"
                        resources.getString(R.string.mp_fraction_half) -> bundleTicket.fraction = "12"
                        resources.getString(R.string.mp_fraction_quarter) -> bundleTicket.fraction = "14"
                    }
                    // Number
                    if ((bundleTicket.date.substring(0, 4) == "3112" && numberEntryMp.text.toString().length == 7) ||
                            (bundleTicket.date.substring(0, 4) != "3112" && numberEntryMp.text.toString().length == 6))
                        bundleTicket.number = numberEntryMp.text.toString()
                    else
                        isNumberOk = false
                }
                5, 6 -> { // Super Piyango, Banko Piyango
                    bundleTicket.ticketType = 1 // Terminal MP
                    // Fraction
                    bundleTicket.fraction = "0"
                    // Number
                    if ((gameIndex == 5 && numberEntryMp.text.toString().length == 6) || (gameIndex == 6 && numberEntryMp.text.toString().length == 5))
                        bundleTicket.number = numberEntryMp.text.toString()
                    else
                        isNumberOk = false
                }
                else -> {
                    bundleTicket.fraction = rowNumberValue.text.toString()
                    storeSelectedNumbers(currentSelectionRow)
                    // Number
                    var numberString = String()
                    val targetLength = when (gameIndex) { // Required length of each row
                        1, 2, 7, 8 -> 12
                        3, 9 -> 20
                        else -> 12
                    }
                    // Process each row
                    for (i in 0 until rowNum) {
                        if (selectedNumbers[i].length != targetLength) {
                            isNumberOk = false
                            break
                        }
                        else
                            numberString = "$numberString${selectedNumbers[i]}"
                    }
                    if (isNumberOk) {
                        bundleTicket.number = numberString
                    }
                }
            }

            // Go to result fragment if all information is as expected
            if (isNumberOk) {
                // Set bundle to pass to result fragment
                val resultBundle = Bundle()
                resultBundle.putSerializable("Ticket", bundleTicket)

                // Go to result fragment
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED // Collapse bottom sheet to make it invisible if returned to this screen via back button
                val resultFragment = ResultFragment()
                resultFragment.arguments = resultBundle
                val fragmentTransaction: FragmentTransaction = requireActivity().supportFragmentManager.beginTransaction() // To change to a different fragment
                fragmentTransaction.setCustomAnimations(R.animator.flip_right_in, R.animator.flip_right_out, R.animator.flip_left_in, R.animator.flip_left_out)
                fragmentTransaction.replace(R.id.fragment_container, resultFragment)
                fragmentTransaction.addToBackStack(null) // To enable back button come back to scan fragment
                fragmentTransaction.commit()
            }
            // Number is not correct, warn user
            else {
                when (gameIndex) {
                    0, 5 -> { // Milli Piyango, Super Piyango
                        if (bundleTicket.date.substring(0, 4) == "3112")
                            Snackbar.make(coordinatorLayout, getString(R.string.warning_incorrect_number_mp_long), Snackbar.LENGTH_LONG).show()
                        else
                            Snackbar.make(coordinatorLayout, getString(R.string.warning_incorrect_number_mp), Snackbar.LENGTH_LONG).show()
                    }
                    3, 9 -> Snackbar.make(coordinatorLayout, getString(R.string.warning_incorrect_number_on), Snackbar.LENGTH_LONG).show() // On Numara
                    4, 10 -> Snackbar.make(coordinatorLayout, getString(R.string.warning_incorrect_number_st), Snackbar.LENGTH_LONG).show() // Sans Topu
                    6 -> Snackbar.make(coordinatorLayout, getString(R.string.warning_incorrect_number_bp), Snackbar.LENGTH_LONG).show() // Banko Piyango
                    else -> Snackbar.make(coordinatorLayout, getString(R.string.warning_incorrect_number), Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    /**
     * Updates chips' selection based on selected numbers strings.
     * (Only current number selection tab is updated)
     */
    private fun updateChips() {
        val chips = viewPagerRows.findViewWithTag<ChipGroup>("chipGroup$currentSelectionRow")
        if (chips != null) {
            val rowIndex = viewPagerRows.currentItem
            var nums = selectedNumbers[rowIndex]
            // If Sans Topu -> Set extra number
            if ((gameIndex == 4 || gameIndex == 10) && nums.isNotEmpty()) {
                val chipsTwo = viewPagerRows.findViewWithTag<ChipGroup>("chipGroupTwo$currentSelectionRow")
                // Clear all
                for (i in 0 until chipsTwo.childCount) {
                    (chipsTwo.getChildAt(i) as Chip).isChecked = false
                }
                // Set selected
                val c = chipsTwo.getChildAt(nums.substring(nums.length - 2, nums.length).toInt() - 1) as Chip
                c.isChecked = true
                nums = nums.substring(0, nums.length - 2) // Remove already checked number
            }
            // Clear all
            for (i in 0 until chips.childCount) {
                (chips.getChildAt(i) as Chip).isChecked = false
            }
            // Set selected
            for (i in 0 until nums.length / 2) {
                val c = chips.getChildAt(nums.substring(2 * i, 2 * (i + 1)).toInt() - 1) as Chip
                c.isChecked = true
            }
        }
    }

    /**
     * Updates selected numbers string at a specific row
     */
    private fun storeSelectedNumbers(currentSelectionRow: Int) {
        // Get reference to ChipGroup
        val chips = viewPagerRows.findViewWithTag<ChipGroup>("chipGroup$currentSelectionRow")
        // Process all children to read selected numbers
        if (chips != null && chips.childCount != 0) {
            var nums = "" // All the numbers in the current row
            for (i in 0 until chips.childCount) {
                if (chips.getChildAt(i) != null) {
                    val c = chips.getChildAt(i) as Chip
                    if (c.isChecked) {
                        nums = "$nums${String.format("%02d", c.text.toString().toInt())}"
                    }
                }
            }
            if ((gameIndex == 4 || gameIndex == 10) && nums.length != 10) // Separate length check is required for the first 5 numbers
                nums = ""

            // Consider extra number if Sans Topu is selected
            if (gameIndex == 4 || gameIndex == 10) {
                val chipsTwo = viewPagerRows.findViewWithTag<ChipGroup>("chipGroupTwo$currentSelectionRow")
                if (chipsTwo != null && chipsTwo.childCount != 0) {
                    for (i in 0 until chipsTwo.childCount) {
                        if (chipsTwo.getChildAt(i) != null) {
                            val c = chipsTwo.getChildAt(i) as Chip
                            if (c.isChecked) {
                                nums = "$nums${String.format("%02d", c.text.toString().toInt())}"
                            }
                        }
                    }
                }
                if (nums.length != 12) // Separate length check is required for the last number
                    nums = ""
            }
            selectedNumbers[currentSelectionRow] = nums
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_generate_ticket -> {
                val decoyLayout = fragmentView.findViewById<ViewStub>(R.id.bottom_sheet)
                // Inflate bottom sheet layout contents if not already
                if (decoyLayout != null) {
                    val bottomSheetView: View = decoyLayout.inflate()
                    // Get view references of bottom sheet items
                    gameTypeName = bottomSheetView.findViewById(R.id.text_lottery_type) // Game type selector
                    rowNumberValue = bottomSheetView.findViewById(R.id.text_row_number) // Row number selector
                    rowNumberLayout = bottomSheetView.findViewById(R.id.layout_row_number) // Layout for row number
                    fractionValue = bottomSheetView.findViewById(R.id.text_fraction) // Fraction selector
                    fractionLayout = bottomSheetView.findViewById(R.id.layout_fraction) // Layout for fraction
                    methodSelector = bottomSheetView.findViewById(R.id.method_selector) // Segmented button group for manual/auto number generation
                    numberLayout = bottomSheetView.findViewById(R.id.layout_number) // Number selector with tab layout
                    numberLayoutMp = bottomSheetView.findViewById(R.id.layout_number_mp) // Number selector with text edit for Milli Piyango
                    dateValue = bottomSheetView.findViewById(R.id.text_date)
                    numberEntryMp = bottomSheetView.findViewById(R.id.text_number_mp)
                    tabLayoutRows = bottomSheetView.findViewById(R.id.tab_layout_rows)
                    buttonCancel = bottomSheetView.findViewById(R.id.button_cancel)
                    buttonAdd = bottomSheetView.findViewById(R.id.button_add)
                    viewPagerRows = bottomSheetView.findViewById(R.id.view_pager_rows) // For different rows in bottom sheet
                    focusStealer = bottomSheetView.findViewById(R.id.focus_stealer)

                    // Set view pager parameters
                    viewPagerRows.offscreenPageLimit = 4 // Required to prevent recycling views which cause problems when checking selected numbers after page is scrolled

                    // Set view item attributes
                    populateBottomSheet()
                }

                // Expand if collapsed
                if(bottomSheetBehavior.state != BottomSheetBehavior.STATE_EXPANDED) {
                    scrollView.smoothScrollTo(0, 0) // Scroll to top before possible next expansion
                    bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
                }
                // Collapse if expanded
                else {
                    bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                }
            }
        }
        return super.onOptionsItemSelected(item)
    }

    interface FragmentUiStatusListener {
        fun onFragmentUiAvailable() // Used to start tutorial that uses fragment's tab layout view
    }

    fun setFragmentUiAvailableListener(callback: FragmentUiStatusListener) {
        this.fragmentUiStatusCallback = callback
    }

    private fun closeKeyboard() {
        val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(fragmentView.windowToken, 0)
    }
}
