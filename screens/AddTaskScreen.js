import React, { useState } from 'react';
import { View, Text, TextInput, TouchableOpacity, StyleSheet, Alert } from 'react-native';

export default function AddTaskScreen({ tasks, setTasks, setTab }) {
  const [name, setName] = useState('');
  const [priority, setPriority] = useState('important');

  const add = () => {
    if (!name.trim()) return Alert.alert('Enter a task name');
    setTasks([...tasks, { name, priority, created: new Date().toISOString(), completed: false, skipped: false }]);
    setName(''); setTab('calendar');
  };

  return (
    <View style={styles.container}>
      <TextInput style={styles.input} placeholder="Task name (e.g. Drink water)" placeholderTextColor="#556677" value={name} onChangeText={setName} />
      <Text style={styles.subtitle}>Priority</Text>
      <View style={styles.row}>
        {['not so important', 'important', 'very important'].map(p => (
          <TouchableOpacity key={p} style={[styles.btn, priority === p && { borderColor: p === 'very important' ? '#ff3b3b' : p === 'important' ? '#ffaa00' : '#00ffa3', backgroundColor: priority === p ? '#0d1b2a' : '#060b14' }]} onPress={() => setPriority(p)}>
            <Text style={[styles.btnText, { color: p === 'very important' ? '#ff3b3b' : p === 'important' ? '#ffaa00' : '#e2f3ff' }]}>{p}</Text>
          </TouchableOpacity>
        ))}
      </View>
      <TouchableOpacity style={styles.saveBtn} onPress={add}>
        <Text style={styles.saveText}>SAVE TASK</Text>
      </TouchableOpacity>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, padding: 20 },
  input: { backgroundColor: '#0d1b2a', color: '#e2f3ff', borderWidth: 1, borderColor: '#1a2a3a', borderRadius: 12, padding: 14, fontSize: 16, marginBottom: 20 },
  subtitle: { color: '#8899aa', fontSize: 12, letterSpacing: 2, marginBottom: 8 },
  row: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: 30 },
  btn: { flex: 1, borderWidth: 1, borderColor: '#1a2a3a', borderRadius: 10, padding: 12, alignItems: 'center', marginHorizontal: 4 },
  btnText: { fontSize: 11, fontWeight: 'bold', textTransform: 'uppercase' },
  saveBtn: { backgroundColor: '#ff6b35', borderRadius: 14, padding: 16, alignItems: 'center' },
  saveText: { color: '#fff', fontWeight: 'bold', letterSpacing: 2 },
});
