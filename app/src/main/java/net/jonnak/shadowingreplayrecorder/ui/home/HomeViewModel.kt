package net.jonnak.shadowingreplayrecorder.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class HomeViewModel : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        value = "Hold the button to record"
    }
    val text: LiveData<String> = _text


    fun updateText(newText: String) {
        _text.value = newText
    }
}