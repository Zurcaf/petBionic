package com.example.petbionic

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Session(
    val id: String,
    val device: String,
    val startMs: Long,
    val readingCount: Int = 0
)

class HistoryActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var layoutEmpty: LinearLayout
    private lateinit var progressBar: ProgressBar
    private val sessions = mutableListOf<Session>()
    private lateinit var adapter: SessionAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        recyclerView  = findViewById(R.id.recyclerView)
        layoutEmpty   = findViewById(R.id.layoutEmpty)
        progressBar   = findViewById(R.id.progressBar)

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = SessionAdapter(sessions) { session ->
            val intent = Intent(this, SessionDetailActivity::class.java)
            intent.putExtra("sessionId", session.id)
            startActivity(intent)
        }
        recyclerView.adapter = adapter

        loadSessions()
    }

    private fun loadSessions() {
        progressBar.visibility = View.VISIBLE
        layoutEmpty.visibility = View.GONE

        val db = FirebaseFirestore.getInstance()
        // Field is "startMs" — was incorrectly "timestamp" before
        db.collection("sessions")
            .orderBy("startMs", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { documents ->
                progressBar.visibility = View.GONE
                sessions.clear()
                for (doc in documents) {
                    sessions.add(
                        Session(
                            id       = doc.id,
                            device   = doc.getString("device") ?: "PetBionic",
                            startMs  = doc.getLong("startMs") ?: 0L
                        )
                    )
                }
                adapter.notifyDataSetChanged()

                if (sessions.isEmpty()) {
                    layoutEmpty.visibility = View.VISIBLE
                    return@addOnSuccessListener
                }

                // Load reading counts asynchronously
                sessions.forEachIndexed { index, session ->
                    db.collection("sessions").document(session.id)
                        .collection("readings").get()
                        .addOnSuccessListener { readings ->
                            sessions[index] = session.copy(readingCount = readings.size())
                            adapter.notifyItemChanged(index)
                        }
                }
            }
            .addOnFailureListener {
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Failed to load sessions", Toast.LENGTH_SHORT).show()
                if (sessions.isEmpty()) layoutEmpty.visibility = View.VISIBLE
            }
    }
}

class SessionAdapter(
    private val sessions: List<Session>,
    private val onClick: (Session) -> Unit
) : RecyclerView.Adapter<SessionAdapter.ViewHolder>() {

    private val dateFormat = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault())

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvSessionId : TextView = view.findViewById(R.id.tvSessionId)
        val tvTimestamp : TextView = view.findViewById(R.id.tvTimestamp)
        val tvReadings  : TextView = view.findViewById(R.id.tvReadings)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_session, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val session = sessions[position]
        holder.tvSessionId.text = session.id
        holder.tvTimestamp.text = if (session.startMs > 0L)
            dateFormat.format(Date(session.startMs))
        else
            "Date unknown"
        holder.tvReadings.text = if (session.readingCount > 0)
            "${session.readingCount} readings"
        else
            "Loading..."
        holder.itemView.setOnClickListener { onClick(session) }
    }

    override fun getItemCount() = sessions.size
}
