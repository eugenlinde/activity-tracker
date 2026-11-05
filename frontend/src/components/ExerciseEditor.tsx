import { emptyExercise, emptySet, MAX_EXERCISES, MAX_SETS, type ExerciseDraft, type SetDraft } from '../lib/details'

interface Props {
  value: ExerciseDraft[]
  onChange: (value: ExerciseDraft[]) => void
}

export function ExerciseEditor({ value, onChange }: Props) {
  function updateExercise(index: number, patch: Partial<ExerciseDraft>) {
    onChange(value.map((exercise, i) => (i === index ? { ...exercise, ...patch } : exercise)))
  }

  function removeExercise(index: number) {
    onChange(value.filter((_, i) => i !== index))
  }

  function updateSet(exerciseIndex: number, setIndex: number, patch: Partial<SetDraft>) {
    const sets = value[exerciseIndex].sets.map((set, i) => (i === setIndex ? { ...set, ...patch } : set))
    updateExercise(exerciseIndex, { sets })
  }

  function addSet(exerciseIndex: number) {
    const sets = value[exerciseIndex].sets
    const last = sets[sets.length - 1]
    updateExercise(exerciseIndex, { sets: [...sets, last ? { ...last } : emptySet()] })
  }

  function removeSet(exerciseIndex: number, setIndex: number) {
    updateExercise(exerciseIndex, { sets: value[exerciseIndex].sets.filter((_, i) => i !== setIndex) })
  }

  return (
    <fieldset className="exercise-editor">
      <legend>Exercises</legend>
      {value.map((exercise, index) => (
        <div key={index} className="exercise-edit">
          <div className="row">
            <input
              aria-label="Exercise name"
              placeholder="Exercise name"
              value={exercise.name}
              onChange={(e) => updateExercise(index, { name: e.target.value })}
              required
            />
            <button type="button" onClick={() => removeExercise(index)}>
              Remove
            </button>
          </div>
          <div className="row">
            <input
              aria-label="Target reps"
              placeholder="Target reps (10-15)"
              value={exercise.targetReps}
              onChange={(e) => updateExercise(index, { targetReps: e.target.value })}
            />
            <input
              aria-label="Reps in reserve"
              placeholder="RIR (1-2)"
              value={exercise.repsInReserve}
              onChange={(e) => updateExercise(index, { repsInReserve: e.target.value })}
            />
          </div>
          {exercise.sets.map((set, setIndex) => (
            <div key={setIndex} className="row">
              <span className="muted">#{setIndex + 1}</span>
              <input
                aria-label={`Set ${setIndex + 1} weight`}
                type="number"
                min={0}
                step="any"
                placeholder="kg"
                value={set.weight}
                onChange={(e) => updateSet(index, setIndex, { weight: e.target.value })}
              />
              <input
                aria-label={`Set ${setIndex + 1} reps`}
                type="number"
                min={0}
                step={1}
                placeholder="reps"
                value={set.reps}
                onChange={(e) => updateSet(index, setIndex, { reps: e.target.value })}
              />
              <button type="button" onClick={() => removeSet(index, setIndex)}>
                ×
              </button>
            </div>
          ))}
          <button
            type="button"
            className="link"
            onClick={() => addSet(index)}
            disabled={exercise.sets.length >= MAX_SETS}
          >
            + Add set
          </button>
          <input
            aria-label="Comment"
            placeholder="Comment"
            value={exercise.comment}
            onChange={(e) => updateExercise(index, { comment: e.target.value })}
          />
          <input
            aria-label="Video URL"
            type="url"
            placeholder="Video URL"
            value={exercise.videoUrl}
            onChange={(e) => updateExercise(index, { videoUrl: e.target.value })}
          />
        </div>
      ))}
      <button
        type="button"
        onClick={() => onChange([...value, emptyExercise()])}
        disabled={value.length >= MAX_EXERCISES}
      >
        Add exercise
      </button>
    </fieldset>
  )
}
