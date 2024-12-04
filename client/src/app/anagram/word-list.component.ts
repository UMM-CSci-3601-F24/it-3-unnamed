import { Component, computed, signal } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatFormField } from '@angular/material/form-field';
import { MatRadioModule } from '@angular/material/radio';
import { MatSelectModule } from '@angular/material/select';
import { FormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { WordService } from './word.service';
import { MatSnackBar } from '@angular/material/snack-bar';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { catchError, combineLatest, of, switchMap, tap } from 'rxjs';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatTooltip } from '@angular/material/tooltip';
import { MatNavList } from '@angular/material/list';
import { MatListModule } from '@angular/material/list';
import { MatInputModule } from '@angular/material/input';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { SearchContext } from './searchContext';
import { MatExpansionModule } from '@angular/material/expansion';
import { RoomService } from '../room.service';

@Component({
  selector: 'app-word-list-component',
  standalone: true,
  imports: [
    MatCardModule,
    MatFormField,
    MatRadioModule,
    MatSelectModule,
    MatFormFieldModule,
    FormsModule,
    MatIconModule,
    RouterLink,
    MatButtonModule,
    MatTooltip,
    MatNavList,
    MatListModule,
    MatInputModule,
    MatSlideToggleModule,
    MatExpansionModule
  ],
  templateUrl: './word-list.component.html',
  styleUrl: './word-list.component.scss'
})
export class WordListComponent {

  // client side sorting
  sortType = signal<string | undefined>(undefined);
  sortOrder = signal<boolean | undefined>(false);
  sortByWordOrGroup = signal<string | undefined>(undefined);
  //server side filtering
  contains = signal<string|undefined>('');
  group = signal<string|undefined>(undefined);
  forceUpdate = signal<number>(0);

  filterType = signal<string|undefined>("exact");

  wordGroups: string[];

  errMsg = signal<string | undefined>(undefined);

  constructor(
    private wordService: WordService,
    private roomService: RoomService,
    private snackBar: MatSnackBar) {
      this.loadWordGroups();
  }

  private contains$ = toObservable(this.contains);
  private group$ = toObservable(this.group);
  private filterType$ = toObservable(this.filterType);
  private forceUpdate$ = toObservable(this.forceUpdate);

  serverFilteredContext =
    toSignal(
      combineLatest([this.contains$, this.group$, this.filterType$, this.forceUpdate$]).pipe(
        switchMap(([word, wordGroup, filterType]) =>
          this.wordService.getWords({
            word,
            wordGroup,
            filterType,
          })
        ),
        catchError((err) => {
          if (err.error instanceof ErrorEvent) {
            this.errMsg.set(
              `Problem in the client – Error: ${err.error.message}`
            );
          } else {
            this.errMsg.set(
              `Problem contacting the server – Error Code: ${err.status}\nMessage: ${err.message}`
            );
          }
          this.snackBar.open(this.errMsg(), 'OK', { duration: 6000 });
          return of<SearchContext>();
        }),
        tap(() => {

        })
      )
    );


  filteredWords = computed(() => {
    const serverFilteredWords = this.serverFilteredContext().words;
    return this.wordService.sortWords(serverFilteredWords, {
      sortType: this.sortType(),
      sortOrder: this.sortOrder(),
      sortByWordOrGroup: this.sortByWordOrGroup(),
    });
  });

  /**
   * returns list of searches given by server
   */
  searchHistory = computed(() => {
    const searches = this.serverFilteredContext().searches;
    return searches;
  })

  loadWordGroups() {
    this.roomService.getWordGroups().subscribe(wordGroups => {
      this.wordGroups = wordGroups
    })
  }

  /**
   * calls deleteWord and returns a snackbar
   * @param id - id of word to be deleted
   */
  deleteWord(id: string) {
    this.wordService.deleteWord(id).subscribe({
      next: () => {
        this.snackBar.open(
          `We deleted a word!`,
          "OK",
          { duration: 2000 }
        );
        this.forceUpdate.set(this.forceUpdate() + 1);
      },
      error: err => {
        this.snackBar.open(
          `Problem contacting the server – Error Code: ${err.status}\nMessage: ${err.message}`,
          'OK',
          { duration: 5000 }
        );
      },
    });
  }


  /**
   * Deletes all words in the wordGroup
   * pulls group from the wordGroup search box as of 10/20/24
   * @param group - name of wordGroup to be deleted
   */
  deleteWordGroup(group: string) {
    this.wordService.deleteWordGroup(group).subscribe(() => {
      this.snackBar.open(`We deleted a word group!`, 'OK', {duration: 6000});
    })
  }

  max(arg0: number,arg1: number): number {
    if (arg0 >= arg1){
      return arg0;
    } else {return arg1}
  }
}
