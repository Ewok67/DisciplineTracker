import React, { useState } from 'react';
import { View, Text, ScrollView, TouchableOpacity, StyleSheet, StatusBar } from 'react-native';
import CalendarScreen from './screens/CalendarScreen';
import DashboardScreen from './screens/DashboardScreen';
import AddTaskScreen from './screens/AddTaskScreen';

export default function App() {
  const [tab, setTab] = useState('calendar');
  const [tasks, setTasks] = useState([]);

  return (
    <View style={styles.container}>
      <StatusBar barStyle="light-content" backgroundColor="#060b14" />
      <Text style={styles.header}>PROGRESS TRACKER</Text>
      {tab === 'calendar' && <CalendarScreen tasks={tasks} />}
      {tab === 'dashboard' && <DashboardScreen tasks={tasks} />}
      {tab === 'add' && <AddTaskScreen tasks={tasks} setTasks={setTasks} setTab={setTab} />}
      <View style={styles.nav}>
        {['calendar','dashboard','add'].map(t => (
          <TouchableOpacity key={t} style={[styles.navBtn, tab===t && styles.navActive]} onPress={() => setTab(t)}>
            <Text style={[styles.navText, tab===t && styles.navTextActive]}>{t.toUpperCase()}</Text>
          </TouchableOpacity>
        ))}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#060b14' },
  header: { color: '#e2f3ff', fontSize: 18, fontWeight: 'bold', letterSpacing: 3, padding: 20, textAlign: 'center', backgroundColor: '#060b14' },
  nav: { flexDirection: 'row', justifyContent: 'space-around', padding: 12, borderTopWidth: 1, borderTopColor: '#1a2a3a', backgroundColor: '#060b14' },
  navBtn: { padding: 8, borderRadius: 8 },
  navActive: { backgroundColor: '#ff6b35' },
  navText: { color: '#8899aa', fontSize: 12, fontWeight: 'bold' },
  navTextActive: { color: '#fff' },
});
