/*
 * MIT License
 *
 * Copyright (c) 2021 Gourav Khunger
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.github.gouravkhunger.quotesapp.ui.fragments

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.drawToBitmap
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.github.gouravkhunger.quotesapp.R
import com.github.gouravkhunger.quotesapp.models.Quote
import com.github.gouravkhunger.quotesapp.ui.QuotesActivity
import com.github.gouravkhunger.quotesapp.util.Constants.Companion.MIN_SWIPE_DISTANCE
import com.github.gouravkhunger.quotesapp.util.Resource
import com.github.gouravkhunger.quotesapp.viewmodels.QuoteViewModel
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.activity_quotes.*
import kotlinx.android.synthetic.main.fragment_quote.*
import kotlinx.android.synthetic.main.fragment_quote.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.Exception
import kotlin.math.abs
import kotlin.math.max

class QuoteFragment : Fragment(R.layout.fragment_quote) {

    // variables
    lateinit var viewModel: QuoteViewModel
    private var quote: Quote? = null
    private var quoteShown = false
    private var isBookMarked = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // get the viewmodel and set observers on data
        viewModel = (activity as QuotesActivity).viewModel

        viewModel.quote.observe(viewLifecycleOwner, { response ->

            // change UI based on what type of resource state the quote is
            // currently in
            when (response) {

                is Resource.Loading -> {
                    // quote is loading
                    showProgressBar()
                    noQuote.visibility = View.GONE
                    fab.visibility = View.GONE
                    quoteShare.visibility = View.GONE
                    quoteShown = false
                    quote = null
                }

                is Resource.Success -> {
                    // quote loaded successfully
                    hideProgressBar()
                    noQuote.visibility = View.GONE
                    fab.visibility = View.VISIBLE
                    quoteShare.visibility = View.VISIBLE
                    response.data.let { quoteResponse ->
                        quote = quoteResponse!!
                        quoteTv.text = resources.getString(R.string.quote, quoteResponse.quote)
                        authorTv.text = resources.getString(R.string.author, quoteResponse.author)
                        showTextViews()
                    }
                    quoteShown = true
                }

                is Resource.Error -> {
                    // there was some error while loading quote
                    hideProgressBar()
                    hideTextViews()
                    noQuote.visibility = View.VISIBLE
                    fab.visibility = View.GONE
                    quoteShare.visibility = View.GONE
                    response.message.let {
                        noQuote.text = it
                    }
                    quoteShown = false
                    quote = null
                }

            }
        })

        // observe bookmarked value from view model
        // and update fab icon based on value
        viewModel.bookmarked.observe(viewLifecycleOwner, {
            isBookMarked = it
            fab.setImageDrawable(
                ContextCompat.getDrawable(
                    requireContext(),
                    if (isBookMarked) R.drawable.ic_bookmarked
                    else R.drawable.ic_unbookmarked
                )
            )
        })

        // detect left swipe on the "quote card".
        quoteCard.setOnTouchListener(View.OnTouchListener { v, event ->

            // variables to store current configuration of quote card.
            val displayMetrics = resources.displayMetrics
            val prevX = quoteCard.x
            val prevWidth = quoteCard.width
            val defaultX = (displayMetrics.widthPixels.toFloat()) / 2 - (prevWidth) / 2

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                }

                MotionEvent.ACTION_UP -> {
                    // - If the user picked finger up, then check if the swipe distance
                    //   was more than minimum swipe required to load a new quote
                    // - Load a new quote if swiped adequately
                    if (abs(prevX) > MIN_SWIPE_DISTANCE) viewModel.getRandomQuote()

                    // animate the card to its original position after the swipe was
                    // carried out
                    quoteCard.animate()
                        .x(defaultX)
                        .setDuration(0)
                        .start()
                }

                MotionEvent.ACTION_MOVE -> {
                    // get the new co-ordinate on X-axis to carry out swipe action
                    val newX = event.rawX

                    // carry out swipe only if newX < defaultX that is,
                    // the card is swiped to the left side, not to the
                    // right side
                    if (newX < defaultX + prevWidth) {
                        quoteCard.animate()
                            .x(
                                max(
                                    (newX - prevWidth),
                                    (-MIN_SWIPE_DISTANCE - 150f)
                                )
                            )
                            .setDuration(0)
                            .start()
                    }
                    Log.d("currentX:", "${event.x}")
                }
            }

            // required to by-pass warning
            v.performClick()
            return@OnTouchListener true
        })

        // perform save/delete quote action when fab is clicked
        fab.setOnClickListener {
            if (isBookMarked) {

                // delete quote if it is already bookmarked.
                viewModel.deleteQuote(quote!!)
                if ((activity as QuotesActivity).atHome) Snackbar.make(
                    requireActivity().findViewById(
                        R.id.quotesNavHostFragment
                    ), "Removed Bookmark!", Snackbar.LENGTH_SHORT
                )
                    .apply {
                        setAction("Undo") {
                            viewModel.saveQuote(quote!!)
                            if ((activity as QuotesActivity).atHome) makeSnackBar(view, "Re-saved!")
                            isBookMarked = !isBookMarked
                        }
                        setActionTextColor(ContextCompat.getColor(view.context, R.color.light_blue))
                        show()
                    }

                // work around to hide fab while snackbar is visible
                if (fab != null) fab.visibility = View.INVISIBLE
                this.lifecycleScope.launch(context = Dispatchers.Default) {
                    delay(3000)
                    withContext(Dispatchers.Main) {
                        if (fab != null) fab.visibility = View.VISIBLE
                    }
                }
            } else {
                // save quote if not already saved
                viewModel.saveQuote(quote!!)
                makeSnackBar(view, "Successfully saved Quote!")
            }
        }
        quoteShare.setOnClickListener {
            quoteShare.visibility = View.GONE
            extraText.setText(R.string.extraText)
            val aView = view.cardHolder
            aView.setBackgroundResource(R.drawable.activity_background)
            val bmp = aView.drawToBitmap()
            val bmpPath = MediaStore.Images.Media.insertImage(context?.contentResolver, bmp, "quote_bitmap",null)
            val uri: Uri = Uri.parse(bmpPath)

            // Sharing
            val intent = Intent(Intent.ACTION_SEND)
            intent.type = "image/png"
            try {
                intent.putExtra(Intent.EXTRA_STREAM, uri)
                intent.putExtra(Intent.EXTRA_TEXT, "Shared from QuotesApp")

            }catch (e: Exception){
                Toast.makeText(context, "failed to share! try again", Toast.LENGTH_SHORT).show()
            }
            startActivity(Intent.createChooser(intent,"Share via:"))

            ///getting back to normal
            aView.background = null
            extraText.setText(R.string.info)
            view.quoteShare.visibility = View.VISIBLE
        }
    }

    // function names say it all
    private fun showProgressBar() {
        quoteLoading.visibility = View.VISIBLE
        hideTextViews()
    }

    private fun showTextViews() {
        quoteTv.visibility = View.VISIBLE
        authorTv.visibility = View.VISIBLE
    }

    private fun hideTextViews() {
        quoteTv.visibility = View.GONE
        authorTv.visibility = View.GONE
    }

    private fun hideProgressBar() {
        quoteLoading.visibility = View.GONE
    }

    private fun makeSnackBar(view: View, message: String) {
        if ((activity as QuotesActivity).atHome) {
            Snackbar.make(view, message, Snackbar.LENGTH_SHORT).show()

            // workaround to disable floating action button when a snackbar is made
            // to prevent double clicks while task is executing/snackbar is visible
            if (fab.visibility == View.VISIBLE) {
                if (fab != null) fab.visibility = View.INVISIBLE
                lifecycleScope.launch(context = Dispatchers.Default) {
                    delay(3000)
                    withContext(Dispatchers.Main) {
                        if (fab != null) fab.visibility = View.VISIBLE
                    }
                }
            }
        }
    }
}