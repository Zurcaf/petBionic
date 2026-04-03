package com.example.petbionic

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

data class Session(
    val id: String,
    val device: String,
    val timestamp: Long,
    val readingCount: Int = 0
)

class HistoryActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private val sessions = mutableListOf<Session>()
    private lateinit var adapter: SessionAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        recyclerView = findViewById(R.id.recyclerView)
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
        val db = FirebaseFirestore.getInstance()
        db.collection("sessions")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { documents ->
                sessions.clear()
                for (doc in documents) {
                    val session = Session(
                        id = doc.id,
                        device = doc.getString("device") ?: "Unknown",
                        timestamp = doc.getLong("timestamp") ?: 0L
                    )
                    sessions.add(session)
                }
                // Get reading counts
                sessions.forEachIndexed { index, session ->
                    db.collection("sessions").document(session.id)
                        .collection("readings").get()
                        .addOnSuccessListener { readings ->
                            sessions[index] = session.copy(readingCount = readings.size())
                            adapter.notifyItemChanged(index)
                        }
                }
                adapter.notifyDataSetChanged()
                if (sessions.isEmpty()) {
                    Toast.makeText(this, "No sessions found", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load sessions", Toast.LENGTH_SHORT).show()
            }
    }
}

class SessionAdapter(
    private val sessions: List<Session>,
    private val onClick: (Session) -> Unit
) : RecyclerView.Adapter<SessionAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvSessionId: TextView = view.findViewById(R.id.tvSessionId)
        val tvDevice: TextView = view.findViewById(R.id.tvDevice)
        val tvReadings: TextView = view.findViewById(R.id.tvReadings)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_session, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val session = sessions[position]
        holder.tvSessionId.text = session.id
        holder.tvDevice.text = "Device: ${session.device}"
        holder.tvReadings.text = "${session.readingCount} readings"
        holder.itemView.setOnClickListener { onClick(session) }
    }

    override fun getItemCount() = sessions.size
}