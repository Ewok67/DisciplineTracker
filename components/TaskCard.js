import React from 'react';
import { View, Text, StyleSheet } from 'react-native';

export default function TaskCard({ task }) {
  const color = task.priority === 'very important' ? '#ff3b3b' : task.priority === 'important' ? '#ffaa00' : '#00ffa3';
  return (
    <View style={styles.card}>
      <View style={[styles.bar, { backgroundColor: color }]} />
      <Text style={styles.name}>{task.name}</Text>
      <Text style={styles.meta}>{task.priority.toUpperCase()} • {task.completed ? 'Done' : 'Pending'}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  card: { backgroundColor: '#0d1b2a', borderRadius: 12, padding: 14, marginBottom: 10, borderWidth: 1, borderColor: '#1a2a3a' },
  bar: { height: 4, borderRadius: 2, marginBottom: 10 },
  name: { color: '#e2f3ff', fontSize: 16, fontWeight: 'bold' },
  meta: { color: '#556677', fontSize: 11, marginTop: 6 },
});
