import { Tag } from 'antd'
import type { ActivityDetails } from '../api/types'
import { formatSet } from '../lib/details'

export function ExerciseDetails({ details }: { details: ActivityDetails | null }) {
  const exercises = details?.exerciseList ?? []
  if (exercises.length === 0) return <span className="muted">No exercises recorded.</span>

  return (
    <ol className="exercises">
      {exercises.map((exercise, index) => (
        <li key={index}>
          <div className="exercise-head">
            <strong>
              {exercise.videoUrl ? (
                <a href={exercise.videoUrl} target="_blank" rel="noreferrer">
                  {exercise.name}
                </a>
              ) : (
                exercise.name
              )}
            </strong>
            {exercise.targetReps && <span className="muted">target {exercise.targetReps}</span>}
            {exercise.repsInReserve && <span className="muted">RIR {exercise.repsInReserve}</span>}
          </div>
          {(exercise.workoutSetList ?? []).length > 0 && (
            <div className="sets">
              {(exercise.workoutSetList ?? []).map((set, setIndex) => (
                <Tag key={setIndex} variant="filled">
                  {formatSet(set)}
                </Tag>
              ))}
            </div>
          )}
          {exercise.comment && <div className="muted">{exercise.comment}</div>}
        </li>
      ))}
    </ol>
  )
}
